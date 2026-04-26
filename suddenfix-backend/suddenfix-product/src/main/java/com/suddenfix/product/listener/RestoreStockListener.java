package com.suddenfix.product.listener;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.suddenfix.common.utils.GeneIdGenerator;
import com.suddenfix.product.config.ProductEventRabbitConfig;
import com.suddenfix.product.domain.pojo.Product;
import com.suddenfix.product.domain.pojo.StockFlow;
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

import static com.suddenfix.common.enums.RedisPreMessage.PRODUCT_DB_DEDUCTED;
import static com.suddenfix.common.enums.RedisPreMessage.PRODUCT_DETAIL;
import static com.suddenfix.common.enums.RedisPreMessage.PRODUCT_PREHEAT_HASH;
import static com.suddenfix.common.enums.RedisPreMessage.PRODUCT_RECOMMEND;
import static com.suddenfix.common.enums.RedisPreMessage.PRODUCT_RESTORE_IDEMPOTENT;
import static com.suddenfix.common.enums.RedisPreMessage.PRODUCT_SEARCH;
import static com.suddenfix.common.enums.RedisPreMessage.GOODS_PRE_DEDUCTION;
import static com.suddenfix.common.enums.RedisPreMessage.GOODS_IS_EXIST;
import static com.suddenfix.common.enums.RedisPreMessage.ORDER_STOCK_RESTORED;

@Slf4j
@Component
@RequiredArgsConstructor
public class RestoreStockListener {

    private final ProductMapper productMapper;
    private final StockFlowMapper stockFlowMapper;
    // 注入 Redis 用于实现消费的幂等性
    private final RedisTemplate<String, Object> redisTemplate;

    @RabbitListener(queues = ProductEventRabbitConfig.RESTORE_STOCK_QUEUE)
    @Transactional(rollbackFor = Exception.class)
    public void restoreStock(String payload) {
        log.info("【商品服务】收到恢复真实库存消息: {}", payload);

        try {
            // 解析 payload (对应 OrderWithProduct 对象)
            JSONObject jsonObject = JSONUtil.parseObj(payload);
            Long orderId = jsonObject.getLong("orderId");
            JSONArray productIds = jsonObject.getJSONArray("productIds");
            JSONArray productQuantities = jsonObject.getJSONArray("productQuantities");

            if (orderId == null || productIds == null || productQuantities == null) {
                log.warn("【商品服务】恢复库存消息格式不完整，放弃处理: {}", payload);
                return;
            }

            // 【核心防线】幂等性校验：防止 RabbitMQ 消息重复投递导致库存被加多次
            String idempotentKey = PRODUCT_RESTORE_IDEMPOTENT.getValue() + orderId;
            // 使用 Redis SETNX，如果键不存在则设置成功返回 true，过期时间设为 7 天（覆盖大促周期的补偿）
            Boolean isFirstTime = redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", 7, TimeUnit.DAYS);

            if (Boolean.FALSE.equals(isFirstTime)) {
                log.info("【商品服务】订单 {} 的库存已经恢复过，触发幂等拦截，忽略此重复消息", orderId);
                return;
            }

            // 遍历订单中的商品列表，依次执行 DB 库存增加
            // 遍历订单中的商品列表，依次执行 DB 库存增加
            for (int i = 0; i < productIds.size(); i++) {
                Long productId = productIds.getLong(i);
                Long quantity = productQuantities.getLong(i);

                // 【核心修复：查账】
                // 去 Redis 查一下，这个商品当初到底有没有真正在 DB 里扣减成功？
                String deductedKey = PRODUCT_DB_DEDUCTED.getValue() + orderId + ":" + productId;
                Boolean wasDeducted = redisTemplate.hasKey(deductedKey);

                if (Boolean.TRUE.equals(wasDeducted)) {
                    Product beforeProduct = productMapper.selectById(productId);
                    int beforeStock = beforeProduct == null ? 0 : beforeProduct.getStock();
                    // 当初确实扣了，现在执行加回操作
                    int updateRow = productMapper.restoreStock(productId, quantity);

                    if (updateRow > 0) {
                        Product afterProduct = productMapper.selectById(productId);
                        syncRedisStockAfterRestore(productId, quantity, true);
                        stockFlowMapper.insert(StockFlow.builder()
                                .flowId(GeneIdGenerator.generatorId(productId))
                                .productId(productId)
                                .orderId(orderId)
                                .businessType("ROLLBACK")
                                .changeType(3)
                                .changeAmount(Math.toIntExact(quantity))
                                .beforeStock(beforeStock)
                                .afterStock(afterProduct == null ? beforeStock + Math.toIntExact(quantity) : afterProduct.getStock())
                                .traceId("ORDER_RESTORE_" + orderId + "_" + productId)
                                .operator("system")
                                .remark("订单关闭后恢复真实库存")
                                .createTime(new Date())
                                .build());
                        clearProductCaches(productId);
                        log.info("【商品服务】商品 {} 成功恢复数据库库存: +{}", productId, quantity);
                        // 恢复成功后，销毁账单（保持Redis干净）
                        redisTemplate.delete(deductedKey);
                    } else {
                        log.error("【商品服务】商品 {} 恢复数据库库存失败", productId);
                    }
                } else {
                    syncRedisStockAfterRestore(productId, quantity, false);
                    log.info("【商品服务】商品 {} 当初并未在DB扣减成功，无需恢复库存", productId);
                }
            }
            redisTemplate.opsForValue().set(ORDER_STOCK_RESTORED.getValue() + orderId, "1", 7, TimeUnit.DAYS);

        } catch (Exception e) {
            log.error("【商品服务】恢复库存处理异常，准备重试", e);
            throw e;
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

    private void syncRedisStockAfterRestore(Long productId, Long quantity, boolean dbDeducted) {
        String preheatKey = PRODUCT_PREHEAT_HASH.getValue() + productId;
        if (dbDeducted) {
            redisTemplate.opsForHash().increment(preheatKey, "stock", quantity);
            redisTemplate.opsForHash().put(preheatKey, "exists", "1");
            redisTemplate.opsForValue().increment(GOODS_PRE_DEDUCTION.getValue() + productId, quantity);
            redisTemplate.opsForValue().set(GOODS_IS_EXIST.getValue() + productId, 1, 7, TimeUnit.DAYS);
            return;
        }
        redisTemplate.opsForHash().put(preheatKey, "stock", "0");
        redisTemplate.opsForHash().put(preheatKey, "exists", "0");
        redisTemplate.opsForValue().set(GOODS_PRE_DEDUCTION.getValue() + productId, 0L, 7, TimeUnit.DAYS);
        redisTemplate.opsForValue().set(GOODS_IS_EXIST.getValue() + productId, 0, 7, TimeUnit.DAYS);
    }
}
