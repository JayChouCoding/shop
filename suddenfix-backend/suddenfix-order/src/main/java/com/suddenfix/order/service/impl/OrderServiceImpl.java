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
import com.suddenfix.order.domain.vo.CouponPreheatVO;
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
import static com.suddenfix.common.enums.RedisPreMessage.COUPON_RESERVED;
import static com.suddenfix.common.enums.RedisPreMessage.COUPON_USER_SET;
import static com.suddenfix.common.enums.RedisPreMessage.COUPON_USER_TOKEN_HASH;
import static com.suddenfix.common.enums.RedisPreMessage.PRODUCT_PREHEAT_HASH;
import static com.suddenfix.common.enums.RedisPreMessage.REDIS_PREVENT_DUPLICATION;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements IOrderService {

    private static final DefaultRedisScript<Long> COUPON_RESERVE_SCRIPT = buildCouponReserveScript();
    private static final DefaultRedisScript<Long> COUPON_RELEASE_SCRIPT = buildCouponReleaseScript();
    private static final DefaultRedisScript<Long> COUPON_RECOVER_RESERVE_SCRIPT = buildCouponRecoverReserveScript();

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
        if (orderDTO == null || orderDTO.getIdempotentKey() == null || orderDTO.getIdempotentKey().isBlank()) {
            throw new ServiceException("下单幂等键不能为空");
        }
        if (orderDTO.getProducts() == null || orderDTO.getProducts().isEmpty()) {
            throw new ServiceException("下单商品不能为空");
        }
        // 用 Redis 存储幂等键防止用户重复点击
        String idempotentKey = REDIS_PREVENT_DUPLICATION.getValue() + orderDTO.getIdempotentKey();
        List<Long> successDeductProductIds = new ArrayList<>();
        Map<Long, Long> deductQuantities = new HashMap<>();
        boolean couponReserved = false;
        OrderCreateMessage orderCreateMessage = null;
        try{
            if (Boolean.FALSE.equals(redisTemplate.opsForValue().setIfAbsent(idempotentKey, 1, RedisExpirationTime.EXPIRATION_TIME.getTimeout()))) {
                return Result.fail(ResultCodeEnum.ORDER_IS_EXIST);
            }

            Long orderId = GeneIdGenerator.generatorId(orderDTO.getUserId());
            orderDTO.setOrderId(orderId);
            orderCreateMessage = buildOrderCreateMessage(orderDTO);

            for (Map.Entry<Long, List<Long>> entry : orderDTO.getProducts().entrySet()) {
                Long productId = entry.getKey();
                if (entry.getValue() == null || entry.getValue().isEmpty() || entry.getValue().get(0) == null
                        || entry.getValue().get(0) <= 0) {
                    throw new ServiceException("购买数量必须大于 0");
                }
                Long quantity = entry.getValue().get(0);
                deductQuantities.put(productId, quantity);
                String productPreheatKey = PRODUCT_PREHEAT_HASH.getValue() + productId;
                Object redisStock = redisTemplate.opsForHash().get(productPreheatKey, "stock");
                Object stockExist = redisTemplate.opsForHash().get(productPreheatKey, "exists");
                if (redisStock == null) {
                    redisStock = redisTemplate.opsForValue().get(GOODS_PRE_DEDUCTION.getValue() + productId);
                }
                if (stockExist == null) {
                    stockExist = redisTemplate.opsForValue().get(GOODS_IS_EXIST.getValue() + productId);
                }

                if (stockExist == null || "0".equals(String.valueOf(stockExist)) || redisStock == null || Long.parseLong(String.valueOf(redisStock)) <= 0) {
                    redisTemplate.opsForHash().put(productPreheatKey, "exists", "0");
                    redisTemplate.opsForValue().set(GOODS_IS_EXIST.getValue() + productId, 0, RedisExpirationTime.EXPIRATION_TIME.getTimeout());
                    rollbackRedisStock(successDeductProductIds, deductQuantities);
                    redisTemplate.delete(idempotentKey);
                    return Result.fail("商品已售罄");
                }

                Long stockResult = redisTemplate.execute(stockDeductScript,
                        List.of(
                                productPreheatKey,
                                GOODS_PRE_DEDUCTION.getValue() + productId,
                                GOODS_IS_EXIST.getValue() + productId
                        ),
                        String.valueOf(quantity));

                if(stockResult == -1){
                    rollbackRedisStock(successDeductProductIds, deductQuantities);
                    redisTemplate.delete(idempotentKey);
                    return Result.fail("商品不存在或未预热");
                }
                if(stockResult == 0){
                    rollbackRedisStock(successDeductProductIds, deductQuantities);
                    redisTemplate.delete(idempotentKey);
                    return Result.fail("手慢了，库存不足！");
                }
                // 扣减成功，加入记录
                successDeductProductIds.add(productId);
            }

            reserveCouponIfNeeded(orderCreateMessage);
            couponReserved = hasCoupon(orderCreateMessage);

            rabbitTemplate.convertAndSend(
                    RabbitEventConstants.EVENT_EXCHANGE,
                    TOPIC_ON_CREATE.getTopic(),
                    JSONUtil.toJsonStr(orderCreateMessage)
            );
            return Result.success(orderId);
        }catch (Exception e){
            rollbackRedisStock(successDeductProductIds, deductQuantities);
            if (couponReserved) {
                releaseCouponReservation(orderCreateMessage, orderDTO.getUserId());
            }
            redisTemplate.delete(idempotentKey);
            if (e instanceof ServiceException serviceException) {
                throw serviceException;
            }
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
            if (entry.getValue() == null || entry.getValue().isEmpty() || entry.getValue().get(0) == null
                    || entry.getValue().get(0) <= 0) {
                throw new ServiceException("购买数量必须大于 0");
            }
            ProductSkuDTO sku = productSkuMap.get(entry.getKey());
            if (sku == null || sku.getPrice() == null || sku.getStatus() == null || sku.getStatus() != 1) {
                throw new RuntimeException("商品不存在或已下架，productId=" + entry.getKey());
            }
            totalAmount += entry.getValue().get(0) * sku.getPrice();
        }

        long freightAmount = orderDTO.getFreight() == null ? 0L : orderDTO.getFreight();
        long originalAmount = totalAmount + freightAmount;
        ValidatedCouponSelection couponSelection = resolveCouponSelection(orderDTO, originalAmount);
        long discountAmount = couponSelection.discountAmount();
        long payAmount = originalAmount - discountAmount;
        if (originalAmount > 0 && payAmount <= 0) {
            discountAmount = originalAmount - 1L;
            payAmount = 1L;
        } else {
            payAmount = Math.max(0L, payAmount);
        }

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
                .couponId(couponSelection.couponId())
                .couponSegment(couponSelection.couponSegment())
                .couponToken(couponSelection.couponToken())
                .build();
    }

    // 私有方法用于回滚 Redis 库存
    private void rollbackRedisStock(List<Long> successDeductProductIds, Map<Long, Long> deductQuantities) {
        for (Long pid : successDeductProductIds) {
            Long quantity = deductQuantities.get(pid);
            String preheatKey = PRODUCT_PREHEAT_HASH.getValue() + pid;
            redisTemplate.opsForHash().increment(preheatKey, "stock", quantity);
            redisTemplate.opsForHash().put(preheatKey, "exists", "1");
            redisTemplate.opsForValue().increment(GOODS_PRE_DEDUCTION.getValue() + pid, quantity);
            redisTemplate.opsForValue().set(GOODS_IS_EXIST.getValue() + pid, 1, RedisExpirationTime.EXPIRATION_TIME.getTimeout());
        }
    }

    private void reserveCouponIfNeeded(OrderCreateMessage orderCreateMessage) {
        if (!hasCoupon(orderCreateMessage)) {
            return;
        }
        Long result = redisTemplate.execute(
                COUPON_RESERVE_SCRIPT,
                List.of(
                        COUPON_RESERVED.getValue() + orderCreateMessage.getCouponId(),
                        COUPON_USER_TOKEN_HASH.getValue() + orderCreateMessage.getCouponId(),
                        COUPON_USER_SET.getValue() + orderCreateMessage.getCouponId()
                ),
                String.valueOf(orderCreateMessage.getUserId()),
                orderCreateMessage.getCouponToken(),
                String.valueOf(orderCreateMessage.getOrderId())
        );
        if (Long.valueOf(1L).equals(result)) {
            return;
        }
        if (Long.valueOf(-1L).equals(result) || Long.valueOf(-2L).equals(result)) {
            Long recoverResult = recoverCouponReservationFromDb(orderCreateMessage);
            if (Long.valueOf(1L).equals(recoverResult)) {
                return;
            }
            if (Long.valueOf(0L).equals(recoverResult)) {
                throw new ServiceException("当前优惠券已经被占用，请重新选择");
            }
            throw new ServiceException("优惠券不属于当前用户或已失效，请重新选择");
        }
        if (Long.valueOf(0L).equals(result)) {
            throw new ServiceException("当前优惠券已经被占用，请重新选择");
        }
        throw new ServiceException("优惠券预占失败，请稍后重试");
    }

    private Long recoverCouponReservationFromDb(OrderCreateMessage orderCreateMessage) {
        CouponRecord couponRecord = couponRecordMapper.selectByUserIdAndCouponToken(
                orderCreateMessage.getUserId(),
                orderCreateMessage.getCouponToken()
        );
        if (couponRecord == null || !Objects.equals(couponRecord.getCouponId(), orderCreateMessage.getCouponId())
                || couponRecord.getStatus() == null || couponRecord.getStatus() != 0
                || couponRecord.getOrderId() != null || couponRecord.getSegmentIndex() == null) {
            return -1L;
        }

        String claimJson = JSONUtil.toJsonStr(CouponPreheatVO.builder()
                .couponId(couponRecord.getCouponId())
                .userId(couponRecord.getUserId())
                .segment(couponRecord.getSegmentIndex())
                .couponToken(couponRecord.getCouponToken())
                .build());
        return redisTemplate.execute(
                COUPON_RECOVER_RESERVE_SCRIPT,
                List.of(
                        COUPON_RESERVED.getValue() + orderCreateMessage.getCouponId(),
                        COUPON_USER_TOKEN_HASH.getValue() + orderCreateMessage.getCouponId(),
                        COUPON_USER_SET.getValue() + orderCreateMessage.getCouponId()
                ),
                String.valueOf(orderCreateMessage.getUserId()),
                orderCreateMessage.getCouponToken(),
                claimJson
        );
    }

    private void releaseCouponReservation(OrderCreateMessage orderCreateMessage, Long userId) {
        if (orderCreateMessage == null || orderCreateMessage.getCouponId() == null
                || orderCreateMessage.getCouponToken() == null || orderCreateMessage.getCouponToken().isBlank()
                || userId == null) {
            return;
        }
        redisTemplate.execute(
                COUPON_RELEASE_SCRIPT,
                List.of(
                        COUPON_RESERVED.getValue() + orderCreateMessage.getCouponId(),
                        COUPON_USER_TOKEN_HASH.getValue() + orderCreateMessage.getCouponId(),
                        COUPON_USER_SET.getValue() + orderCreateMessage.getCouponId()
                ),
                String.valueOf(userId),
                orderCreateMessage.getCouponToken()
        );
    }

    private boolean hasCoupon(OrderCreateMessage orderCreateMessage) {
        return orderCreateMessage != null
                && orderCreateMessage.getCouponId() != null
                && orderCreateMessage.getCouponToken() != null
                && !orderCreateMessage.getCouponToken().isBlank();
    }

    private static DefaultRedisScript<Long> buildCouponReserveScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText("""
                local claim = redis.call('hget', KEYS[2], ARGV[1])
                if (claim == false or claim == nil) then
                    return -1
                end
                if redis.call('sismember', KEYS[3], ARGV[1]) == 0 then
                    return -1
                end
                if string.find(tostring(claim), tostring(ARGV[2]), 1, true) == nil then
                    return -2
                end
                if redis.call('hexists', KEYS[1], ARGV[2]) == 1 then
                    return 0
                end
                redis.call('hset', KEYS[1], ARGV[2], claim)
                redis.call('hdel', KEYS[2], ARGV[1])
                redis.call('srem', KEYS[3], ARGV[1])
                return 1
                """);
        return script;
    }

    private static DefaultRedisScript<Long> buildCouponReleaseScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText("""
                local claim = redis.call('hget', KEYS[1], ARGV[2])
                if (claim == false or claim == nil) then
                    return 0
                end
                redis.call('hset', KEYS[2], ARGV[1], claim)
                redis.call('sadd', KEYS[3], ARGV[1])
                redis.call('hdel', KEYS[1], ARGV[2])
                return 1
                """);
        return script;
    }

    private static DefaultRedisScript<Long> buildCouponRecoverReserveScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText("""
                if redis.call('hexists', KEYS[1], ARGV[2]) == 1 then
                    return 0
                end
                redis.call('hset', KEYS[1], ARGV[2], ARGV[3])
                redis.call('hdel', KEYS[2], ARGV[1])
                redis.call('srem', KEYS[3], ARGV[1])
                return 1
                """);
        return script;
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
        OrderCouponVO coupon = couponRecordMapper.selectCouponDetailByOrderId(orderId);
        if (coupon != null) {
            coupon.setDiscountAmount(order.getDiscountAmount());
        }
        return Result.success(OrderViewVO.builder()
                .order(order)
                .items(items)
                .coupon(coupon)
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
        if (!List.of(OrderStatic.PAID.getCode(), OrderStatic.SHIPPED.getCode())
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

        insertLocalMessage(
                order.getUserId(),
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

    private ValidatedCouponSelection resolveCouponSelection(OrderDTO orderDTO, long orderAmount) {
        if (orderDTO == null) {
            return ValidatedCouponSelection.empty();
        }

        boolean hasCouponId = orderDTO.getCouponId() != null;
        boolean hasCouponToken = orderDTO.getCouponToken() != null && !orderDTO.getCouponToken().isBlank();
        if (!hasCouponId && !hasCouponToken) {
            return ValidatedCouponSelection.empty();
        }
        if (!hasCouponId || !hasCouponToken) {
            throw new ServiceException("优惠券参数不完整，请重新选择优惠券后再下单");
        }

        CouponRecord couponRecord = couponRecordMapper.selectByUserIdAndCouponToken(orderDTO.getUserId(), orderDTO.getCouponToken());
        if (couponRecord == null) {
            throw new ServiceException("优惠券不存在或不属于当前用户");
        }
        if (!Objects.equals(couponRecord.getCouponId(), orderDTO.getCouponId())) {
            throw new ServiceException("优惠券信息不匹配，请重新选择优惠券");
        }
        if (orderDTO.getCouponSegment() != null && !Objects.equals(couponRecord.getSegmentIndex(), orderDTO.getCouponSegment())) {
            throw new ServiceException("优惠券分段信息已失效，请重新选择优惠券");
        }
        if (couponRecord.getStatus() == null || couponRecord.getStatus() != 0 || couponRecord.getOrderId() != null) {
            throw new ServiceException("当前优惠券已经被使用或锁定，请重新选择");
        }

        Coupon coupon = couponMapper.selectCoupon(orderDTO.getCouponId());
        if (coupon == null || coupon.getStatus() == null || coupon.getStatus() != 1) {
            throw new ServiceException("优惠券已失效，请重新选择");
        }

        Date now = new Date();
        if (coupon.getStartTime() != null && now.before(coupon.getStartTime())) {
            throw new ServiceException("优惠券活动尚未开始");
        }
        if (coupon.getEndTime() != null && now.after(coupon.getEndTime())) {
            throw new ServiceException("优惠券活动已结束");
        }

        long threshold = toMinorUnits(coupon.getMinPoint());
        if (orderAmount < threshold) {
            throw new ServiceException("当前订单金额未达到优惠券使用门槛");
        }

        long discountAmount = Math.min(orderAmount, toMinorUnits(coupon.getAmount()));
        if (discountAmount <= 0) {
            throw new ServiceException("当前优惠券抵扣金额异常，请重新选择");
        }

        return new ValidatedCouponSelection(
                coupon.getId(),
                couponRecord.getSegmentIndex(),
                couponRecord.getCouponToken(),
                discountAmount
        );
    }

    private long toMinorUnits(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValue();
    }

    private record ValidatedCouponSelection(Long couponId, Integer couponSegment, String couponToken, Long discountAmount) {
        private static ValidatedCouponSelection empty() {
            return new ValidatedCouponSelection(null, null, "", 0L);
        }
    }
}
