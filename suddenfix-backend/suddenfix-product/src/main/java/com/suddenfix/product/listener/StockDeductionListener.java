package com.suddenfix.product.listener;

import cn.hutool.json.JSONUtil;
import com.suddenfix.common.dto.DeductionProductDTO;
import com.suddenfix.common.enums.MsgStatus;
import com.suddenfix.common.utils.GeneIdGenerator;
import com.suddenfix.product.config.ProductEventRabbitConfig;
import com.suddenfix.product.domain.pojo.Msg;
import com.suddenfix.product.domain.pojo.Product;
import com.suddenfix.product.domain.pojo.StockFlow;
import com.suddenfix.product.mapper.MsgMapper;
import com.suddenfix.product.mapper.ProductMapper;
import com.suddenfix.product.mapper.StockFlowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.suddenfix.common.enums.MsgTopic.TOPIC_ORDER_CANCEL_COMPENSATE;
import static com.suddenfix.common.enums.RedisPreMessage.PRODUCT_DB_DEDUCTED;
import static com.suddenfix.common.enums.RedisPreMessage.PRODUCT_DETAIL;
import static com.suddenfix.common.enums.RedisPreMessage.PRODUCT_RECOMMEND;
import static com.suddenfix.common.enums.RedisPreMessage.PRODUCT_RESTORE_IDEMPOTENT;
import static com.suddenfix.common.enums.RedisPreMessage.PRODUCT_SEARCH;

@Slf4j
@RequiredArgsConstructor
@Component
public class StockDeductionListener {

    private final ProductMapper productMapper;
    private final StockFlowMapper stockFlowMapper;
    private final MsgMapper msgMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @RabbitListener(queues = ProductEventRabbitConfig.STOCK_DEDUCTION_QUEUE)
    @Transactional(rollbackFor = Exception.class)
    public void stockDeduction(String payload) {
        DeductionProductDTO deductionProduct = JSONUtil.toBean(payload, DeductionProductDTO.class);

        Long orderId = deductionProduct.getOrderId();
        Long productId = deductionProduct.getProductId();
        Product beforeProduct = productMapper.selectById(productId);
        int beforeStock = beforeProduct == null ? 0 : beforeProduct.getStock();

        // 【新增防线：防止并发错乱】
        // 如果这个订单因为15分钟不支付，已经被 RestoreStockListener 处理过了
        // 那么迟到的扣减消息直接丢弃，不要再扣了！
        String restoreIdempotentKey = PRODUCT_RESTORE_IDEMPOTENT.getValue() + orderId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(restoreIdempotentKey))) {
            log.info("【商品服务】订单 {} 已被取消并恢复，放弃迟到的DB扣减，productId: {}", orderId, productId);
            return;
        }

        // 尝试数据库扣减
        int row = productMapper.deduction(deductionProduct);
        
        if (row > 0) {
            Product afterProduct = productMapper.selectById(productId);
            // 【核心修复：记账】
            // 真正扣减成功了，才在 Redis 里记录一笔“已扣减”账单，有效期设为7天
            String deductedKey = PRODUCT_DB_DEDUCTED.getValue() + orderId + ":" + productId;
            redisTemplate.opsForValue().set(deductedKey, "1", 7, TimeUnit.DAYS);
            clearProductCaches(productId);
            stockFlowMapper.insert(StockFlow.builder()
                    .flowId(GeneIdGenerator.generatorId(productId))
                    .productId(productId)
                    .orderId(orderId)
                    .businessType("ORDER")
                    .changeType(2)
                    .changeAmount(-Math.toIntExact(deductionProduct.getDeductionStock()))
                    .beforeStock(beforeStock)
                    .afterStock(afterProduct == null ? beforeStock - Math.toIntExact(deductionProduct.getDeductionStock()) : afterProduct.getStock())
                    .traceId("ORDER_DEDUCT_" + orderId + "_" + productId)
                    .operator("system")
                    .remark("订单创建后异步确认扣减真实库存")
                    .createTime(new Date())
                    .build());
            log.info("【商品服务】商品 {} 成功扣减DB库存，已记录流水账", productId);
            
        } else {
            log.error("【商品服务】乐观锁扣减失败，引发超卖拦截，productId: {}", productId);
            msgMapper.insertIgnoreMsg(Msg.builder()
                    .msgId(GeneIdGenerator.generatorId(orderId))
                    .businessId(orderId)
                    .topic(TOPIC_ORDER_CANCEL_COMPENSATE.getTopic())
                    .payload(String.valueOf(orderId))
                    .status(MsgStatus.PENDING_SENDING.getStatus())
                    .retryCount(0)
                    .nextRetryTime(new Date())
                    .build());
        }
    }

    private void clearProductCaches(Long productId) {
        redisTemplate.delete(PRODUCT_DETAIL.getValue() + productId);
        clearByPrefix(PRODUCT_SEARCH.getValue());
        clearByPrefix(PRODUCT_RECOMMEND.getValue());
    }

    private void clearByPrefix(String prefix) {
        var keys = redisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
