package com.suddenfix.order.service.impl;

import cn.hutool.json.JSONUtil;
import com.suddenfix.common.constants.RabbitEventConstants;
import com.suddenfix.common.dto.CouponRollbackMessage;
import com.suddenfix.common.dto.OrderCreateMessage;
import com.suddenfix.common.dto.PayRollbackMessage;
import com.suddenfix.common.dto.ProductSkuDTO;
import com.suddenfix.common.enums.*;
import com.suddenfix.common.exception.ServiceException;
import com.suddenfix.common.result.Result;
import com.suddenfix.common.utils.GeneIdGenerator;
import com.suddenfix.order.domain.dto.OrderDTO;
import com.suddenfix.order.domain.pojo.Coupon;
import com.suddenfix.order.domain.pojo.CouponRecord;
import com.suddenfix.order.domain.pojo.Msg;
import com.suddenfix.order.domain.pojo.Order;
import com.suddenfix.order.domain.pojo.OrderItem;
import com.suddenfix.order.domain.pojo.OrderWithProduct;
import com.suddenfix.order.domain.vo.OrderCouponVO;
import com.suddenfix.order.domain.vo.OrderViewVO;
import com.suddenfix.order.feign.ProductFeign;
import com.suddenfix.order.mapper.CouponMapper;
import com.suddenfix.order.mapper.CouponRecordMapper;
import com.suddenfix.order.mapper.MsgMapper;
import com.suddenfix.order.mapper.OrderItemMapper;
import com.suddenfix.order.mapper.OrderMapper;
import com.suddenfix.order.service.IOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static com.suddenfix.common.enums.MsgTopic.TOPIC_ON_CREATE;
import static com.suddenfix.common.enums.RedisPreMessage.GOODS_IS_EXIST;
import static com.suddenfix.common.enums.RedisPreMessage.GOODS_PRE_DEDUCTION;
import static com.suddenfix.common.enums.RedisPreMessage.REDIS_PREVENT_DUPLICATION;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements IOrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CouponMapper couponMapper;
    private final CouponRecordMapper couponRecordMapper;
    private final MsgMapper msgMapper;
    private final ProductFeign productFeign;
    private final RedisTemplate<String,Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final DefaultRedisScript<Long> stockDeductScript;


    @Override
    public Result<Long> createOrder(OrderDTO orderDTO) {
        // 用 Redis 存储幂等键防止用户重复点击
        String idempotentKey = REDIS_PREVENT_DUPLICATION + orderDTO.getIdempotentKey();
        List<Long> successDeductProductIds = new ArrayList<>();
        Map<Long, Long> deductQuantities = new HashMap<>();
        try{
            if (Boolean.FALSE.equals(redisTemplate.opsForValue().setIfAbsent(idempotentKey, 1, RedisExpirationTime.EXPIRATION_TIME.getTimeout()))) {
                return Result.fail(ResultCodeEnum.ORDER_IS_EXIST);
            }

            Long orderId = GeneIdGenerator.generatorId(orderDTO.getUserId());
            orderDTO.setOrderId(orderId);
            OrderCreateMessage orderCreateMessage = buildOrderCreateMessage(orderDTO);

            for (Map.Entry<Long, List<Long>> entry : orderDTO.getProducts().entrySet()) {
                Long productId = entry.getKey();
                Long quantity = entry.getValue().get(0);
                deductQuantities.put(productId, quantity);
                Object redisStock = redisTemplate.opsForValue().get(GOODS_PRE_DEDUCTION.getValue() + productId);
                Object stockExist = redisTemplate.opsForValue().get(GOODS_IS_EXIST.getValue() + productId);

                if (stockExist == null || "0".equals(String.valueOf(stockExist)) || redisStock == null || Long.parseLong(String.valueOf(redisStock)) <= 0) {
                    redisTemplate.opsForValue().set(GOODS_IS_EXIST.getValue() + productId, 0, RedisExpirationTime.EXPIRATION_TIME.getTimeout());
                    rollbackRedisStock(successDeductProductIds, deductQuantities);
                    redisTemplate.delete(idempotentKey);
                    return Result.fail("商品已售罄");
                }

                Long stockResult = redisTemplate.execute(stockDeductScript,
                        Collections.singletonList(GOODS_PRE_DEDUCTION.getValue() + productId), String.valueOf(quantity));

                if(stockResult == -1){
                    rollbackRedisStock(successDeductProductIds, deductQuantities);
                    redisTemplate.delete(idempotentKey);
                    return Result.fail("商品不存在或未预热");
                }
                if(stockResult == 0){
                    redisTemplate.opsForValue().set(GOODS_IS_EXIST.getValue() + productId, 0, RedisExpirationTime.EXPIRATION_TIME.getTimeout());
                    rollbackRedisStock(successDeductProductIds, deductQuantities);
                    redisTemplate.delete(idempotentKey);
                    return Result.fail("手慢了，库存不足！");
                }
                // 扣减成功，加入记录
                successDeductProductIds.add(productId);
            }

            rabbitTemplate.convertAndSend(
                    RabbitEventConstants.EVENT_EXCHANGE,
                    TOPIC_ON_CREATE.getTopic(),
                    JSONUtil.toJsonStr(orderCreateMessage)
            );
            return Result.success(orderId);
        }catch (Exception e){
            rollbackRedisStock(successDeductProductIds, deductQuantities);
            redisTemplate.delete(idempotentKey);
            throw new RuntimeException("系统下单失败",e);
        }
    }

    private OrderCreateMessage buildOrderCreateMessage(OrderDTO orderDTO) {
        List<Long> productIds = new ArrayList<>(orderDTO.getProducts().keySet());
        Result<List<ProductSkuDTO>> productBatchResult = productFeign.getProductsByIds(productIds);
        List<ProductSkuDTO> productSkus = productBatchResult == null || productBatchResult.getData() == null
                ? Collections.emptyList()
                : productBatchResult.getData();
        Map<Long, ProductSkuDTO> productSkuMap = new HashMap<>();
        for (ProductSkuDTO productSku : productSkus) {
            productSkuMap.put(productSku.getId(), productSku);
        }
        if (productSkuMap.size() != productIds.size()) {
            throw new RuntimeException("部分商品不存在或已下架，无法创建订单");
        }

        long totalAmount = 0L;
        for (Map.Entry<Long, List<Long>> entry : orderDTO.getProducts().entrySet()) {
            ProductSkuDTO sku = productSkuMap.get(entry.getKey());
            if (sku == null || sku.getPrice() == null || sku.getStatus() == null || sku.getStatus() != 1) {
                throw new RuntimeException("商品不存在或已下架，productId=" + entry.getKey());
            }
            totalAmount += entry.getValue().get(0) * sku.getPrice();
        }

        long freightAmount = orderDTO.getFreight() == null ? 0L : orderDTO.getFreight();
        long discountAmount = orderDTO.getDiscountAmount() == null ? 0L : orderDTO.getDiscountAmount();
        long payAmount = Math.max(0L, totalAmount + freightAmount - discountAmount);

        return OrderCreateMessage.builder()
                .orderId(orderDTO.getOrderId())
                .userId(orderDTO.getUserId())
                .products(orderDTO.getProducts())
                .productNames(orderDTO.getProductNames())
                .totalAmount(totalAmount)
                .freightAmount(freightAmount)
                .discountAmount(discountAmount)
                .payAmount(payAmount)
                .receiverName(orderDTO.getReceiverName())
                .receiverPhone(orderDTO.getReceiverPhone())
                .receiverAddress(orderDTO.getReceiverAddress())
                .remark(orderDTO.getRemark())
                .payChannel(orderDTO.getPayChannel())
                .couponId(orderDTO.getCouponId())
                .couponSegment(orderDTO.getCouponSegment())
                .couponToken(orderDTO.getCouponToken())
                .build();
    }

    // 私有方法用于回滚 Redis 库存
    private void rollbackRedisStock(List<Long> successDeductProductIds, Map<Long, Long> deductQuantities) {
        for (Long pid : successDeductProductIds) {
            redisTemplate.opsForValue().increment(GOODS_PRE_DEDUCTION.getValue() + pid, deductQuantities.get(pid));
        }
    }

    @Override
    public Result<Integer> getUserOrderStatus(Long userId, Long orderId) {
        Order order = orderMapper.selectByOrderId(orderId);
        if (order == null) {
            return Result.success(OrderStatic.INIT.getCode());
        }
        if (!Objects.equals(order.getUserId(), userId)) {
            return Result.fail("订单不存在");
        }
        return Result.success(order.getStatus());
    }

    @Override
    public Result<List<OrderViewVO>> listUserOrders(Long userId) {
        List<Order> orders = orderMapper.selectByUserId(userId);
        List<OrderViewVO> result = orders.stream()
                .map(order -> OrderViewVO.builder()
                        .order(order)
                        .items(orderItemMapper.selectByOrderIdAndUserId(order.getOrderId(), userId))
                        .build())
                .toList();
        return Result.success(result);
    }

    @Override
    public Result<OrderViewVO> getUserOrderDetail(Long userId, Long orderId) {
        Order order = orderMapper.selectByOrderId(orderId);
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            return Result.fail("订单不存在");
        }
        List<OrderItem> items = orderItemMapper.selectByOrderIdAndUserId(orderId, userId);
        return Result.success(OrderViewVO.builder()
                .order(order)
                .items(items)
                .build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> cancelUserOrder(Long userId, Long orderId) {
        Order order = requireOwnedOrder(userId, orderId);
        if (!OrderStatic.PENDING_PAYMENT.getCode().equals(order.getStatus())) {
            throw new ServiceException("当前订单不支持取消，只有待支付订单可以直接取消");
        }
        int updated = orderMapper.cancelOrder(userId, orderId);
        if (updated <= 0) {
            throw new ServiceException("订单取消失败，请刷新后重试");
        }

        restoreStockForClosedOrder(order);
        rollbackCouponIfNeeded(userId, orderId);
        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> refundUserOrder(Long userId, Long orderId) {
        Order order = requireOwnedOrder(userId, orderId);
        if (!List.of(OrderStatic.PAID.getCode(), OrderStatic.SHIPPED.getCode(), OrderStatic.COMPLETED.getCode())
                .contains(order.getStatus())) {
            throw new ServiceException("当前订单状态暂不支持申请退货退款");
        }
        if (order.getOutTradeNo() == null || order.getOutTradeNo().isBlank()) {
            throw new ServiceException("订单缺少支付流水号，暂时无法发起退款");
        }

        int updated = orderMapper.closePaidOrder(userId, orderId);
        if (updated <= 0) {
            throw new ServiceException("退款申请提交失败，请刷新后重试");
        }

        restoreStockForClosedOrder(order);
        insertLocalMessage(
                userId,
                MsgTopic.TOPIC_PAY_REFUND.getTopic(),
                JSONUtil.toJsonStr(PayRollbackMessage.builder()
                        .orderId(orderId)
                        .outTradeNo(order.getOutTradeNo())
                        .refundAmount(order.getPayAmount())
                        .refundNo("REFUND_" + orderId)
                        .build())
        );
        rollbackCouponIfNeeded(userId, orderId);
        return Result.success();
    }

    private Order requireOwnedOrder(Long userId, Long orderId) {
        Order order = orderMapper.selectByOrderId(orderId);
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw new ServiceException("订单不存在");
        }
        return order;
    }

    private void restoreStockForClosedOrder(Order order) {
        List<OrderItem> items = orderItemMapper.selectByOrderIdAndUserId(order.getOrderId(), order.getUserId());
        if (items == null || items.isEmpty()) {
            return;
        }

        for (OrderItem item : items) {
            redisTemplate.opsForValue().increment(GOODS_PRE_DEDUCTION.getValue() + item.getProductId(), item.getQuantity());
            redisTemplate.opsForValue().set(
                    GOODS_IS_EXIST.getValue() + item.getProductId(),
                    1,
                    RedisExpirationTime.EXPIRATION_TIME.getTimeout()
            );
        }

        rabbitTemplate.convertAndSend(
                RabbitEventConstants.EVENT_EXCHANGE,
                MsgTopic.TOPIC_RESTORE_STOCK.getTopic(),
                JSONUtil.toJsonStr(OrderWithProduct.builder()
                        .orderId(order.getOrderId())
                        .orderNo(order.getOrderNo())
                        .userId(order.getUserId())
                        .payAmount(order.getPayAmount())
                        .status(order.getStatus())
                        .payChannel(order.getPayChannel())
                        .productIds(items.stream().map(OrderItem::getProductId).toList())
                        .productQuantities(items.stream().map(OrderItem::getQuantity).toList())
                        .build())
        );
    }

    private void rollbackCouponIfNeeded(Long userId, Long orderId) {
        CouponRecord couponRecord = couponRecordMapper.selectByOrderId(orderId);
        if (couponRecord == null) {
            return;
        }

        couponRecordMapper.rollbackCouponUsedByOrderId(orderId);
        couponRecordMapper.clearCouponBindingByOrderId(orderId);
        if (couponRecord.getCouponId() != null && couponRecord.getCouponToken() != null && couponRecord.getSegmentIndex() != null) {
            insertLocalMessage(
                    userId,
                    MsgTopic.TOPIC_COUPON_ROLLBACK.getTopic(),
                    JSONUtil.toJsonStr(CouponRollbackMessage.builder()
                            .orderId(orderId)
                            .userId(userId)
                            .couponId(couponRecord.getCouponId())
                            .segment(couponRecord.getSegmentIndex())
                            .couponToken(couponRecord.getCouponToken())
                            .build())
            );
        }
    }

    private void insertLocalMessage(Long userId, String topic, String payload) {
        Msg msg = Msg.builder()
                .msgId(GeneIdGenerator.generatorId(userId))
                .businessId(GeneIdGenerator.generatorId(userId))
                .topic(topic)
                .payload(payload)
                .status(MsgStatus.PENDING_SENDING.getStatus())
                .retryCount(0)
                .nextRetryTime(new Date())
                .build();
        int insertRow = msgMapper.insertMsg(msg);
        if (insertRow <= 0) {
            throw new ServiceException("消息写入失败，请稍后重试");
        }
    }
}
