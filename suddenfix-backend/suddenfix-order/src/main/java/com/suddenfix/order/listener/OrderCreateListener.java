package com.suddenfix.order.listener;

import cn.hutool.json.JSONUtil;
import com.suddenfix.common.constants.RabbitEventConstants;
import com.suddenfix.common.dto.DeductionProductDTO;
import com.suddenfix.common.dto.OrderCreateMessage;
import com.suddenfix.common.dto.OrderCreatedMessage;
import com.suddenfix.common.dto.ProductSkuDTO;
import com.suddenfix.common.enums.Exist;
import com.suddenfix.common.enums.MsgStatus;
import com.suddenfix.common.enums.MsgTopic;
import com.suddenfix.common.enums.OrderStatic;
import com.suddenfix.common.enums.ResultCodeEnum;
import com.suddenfix.common.exception.ServiceException;
import com.suddenfix.common.result.Result;
import com.suddenfix.common.utils.GeneIdGenerator;
import com.suddenfix.order.domain.pojo.Msg;
import com.suddenfix.order.domain.pojo.Order;
import com.suddenfix.order.domain.pojo.OrderItem;
import com.suddenfix.order.domain.pojo.OrderWithProduct;
import com.suddenfix.order.feign.ProductFeign;
import com.suddenfix.order.mapper.CouponRecordMapper;
import com.suddenfix.order.mapper.MsgMapper;
import com.suddenfix.order.mapper.OrderItemMapper;
import com.suddenfix.order.mapper.OrderMapper;
import com.suddenfix.order.config.OrderEventRabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import static com.suddenfix.common.enums.MsgTopic.TOPIC_STOCK_DEDUCTION;

@RequiredArgsConstructor
@Slf4j
@Component
public class OrderCreateListener {

    private final OrderMapper orderMapper;
    private final MsgMapper msgMapper;
    private final CouponRecordMapper couponRecordMapper;
    private final ProductFeign productFeign;
    private final OrderItemMapper orderItemMapper;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = OrderEventRabbitConfig.ON_CREATE_QUEUE)
    @Transactional(rollbackFor = Exception.class)
    public void listen(String payload) {
        OrderCreateMessage orderMessage = JSONUtil.toBean(payload, OrderCreateMessage.class);
        if (orderMessage.getOrderId() == null || orderMessage.getUserId() == null
                || orderMessage.getProducts() == null || orderMessage.getProducts().isEmpty()) {
            throw new ServiceException("订单创建消息缺少必要字段，无法继续建单");
        }
        String orderNo = "B" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%06d", new Random().nextInt(1000000));
        long freightAmount = orderMessage.getFreightAmount() == null ? 0L : orderMessage.getFreightAmount();
        long discountAmountTotal = orderMessage.getDiscountAmount() == null ? 0L : orderMessage.getDiscountAmount();
        List<Long> productIds = new ArrayList<>(orderMessage.getProducts().keySet());
        Result<List<ProductSkuDTO>> productBatchResult = productFeign.getProductsByIds(productIds);
        List<ProductSkuDTO> productSkus = productBatchResult == null || productBatchResult.getData() == null
                ? Collections.emptyList()
                : productBatchResult.getData();
        Map<Long, ProductSkuDTO> productSkuMap = productSkus.stream()
                .collect(Collectors.toMap(ProductSkuDTO::getId, sku -> sku, (left, right) -> left, HashMap::new));

        if (productSkuMap.size() != productIds.size()) {
            throw new ServiceException("部分商品不存在或已下架，无法创建订单");
        }

        Long totalAmount = orderMessage.getProducts().entrySet().stream()
                .mapToLong(entry -> {
                    ProductSkuDTO sku = productSkuMap.get(entry.getKey());
                    if (sku == null || sku.getStatus() == null || sku.getStatus() != 1) {
                        throw new ServiceException("商品不存在或已下架，productId=" + entry.getKey());
                    }
                    return entry.getValue().get(0) * sku.getPrice();
                })
                .sum();

        long payAmount = Math.max(0L, totalAmount + freightAmount - discountAmountTotal);

        Order order = Order.builder()
                .orderId(orderMessage.getOrderId())
                .orderNo(orderNo)
                .userId(orderMessage.getUserId())
                .totalAmount(totalAmount)
                .freightAmount(freightAmount)
                .discountAmount(discountAmountTotal)
                .payAmount(payAmount)
                .status(OrderStatic.INIT.getCode())
                .receiverName(orderMessage.getReceiverName())
                .receiverPhone(orderMessage.getReceiverPhone())
                .receiverAddress(orderMessage.getReceiverAddress())
                .remark(orderMessage.getRemark())
                .isDeleted(Exist.IS_NOT_EXIST.getIsExist())
                .payChannel(orderMessage.getPayChannel())
                .build();
        int insertRow = orderMapper.insertOrder(order);
        if (insertRow <= 0) {
            throw new ServiceException(ResultCodeEnum.INSERT_ORDER_ERROR);
        }

        if (orderMessage.getCouponToken() != null && !orderMessage.getCouponToken().isBlank()) {
            couponRecordMapper.bindOrderToCoupon(orderMessage.getUserId(), orderMessage.getCouponToken(), orderMessage.getOrderId());
        }

        List<OrderItem> orderItemList = new ArrayList<>();
        List<DeductionProductDTO> deductionProducts = new ArrayList<>();
        List<Long> productQuantities = new ArrayList<>();
        long allocatedDiscount = 0L;
        int index = 0;

        for (Map.Entry<Long, List<Long>> entry : orderMessage.getProducts().entrySet()) {
            Long productId = entry.getKey();
            Long quantity = entry.getValue().get(0);
            ProductSkuDTO productSku = productSkuMap.get(productId);
            if (productSku == null || productSku.getPrice() == null) {
                throw new ServiceException("商品快照缺失，无法创建订单，productId=" + productId);
            }

            Long itemTotal = quantity * productSku.getPrice();
            Long discountAmount = index == orderMessage.getProducts().size() - 1
                    ? discountAmountTotal - allocatedDiscount
                    : itemTotal * discountAmountTotal / totalAmount;
            if (discountAmount > itemTotal) {
                discountAmount = itemTotal;
            }

            orderItemList.add(OrderItem.builder()
                    .itemId(GeneIdGenerator.generatorId(orderMessage.getUserId()))
                    .orderId(orderMessage.getOrderId())
                    .userId(orderMessage.getUserId())
                    .productId(productId)
                    .productName(productSku.getName())
                    .price(productSku.getPrice())
                    .quantity(quantity)
                    .totalAmount(itemTotal)
                    .discountAmount(discountAmount)
                    .realPayAmount(itemTotal - discountAmount)
                    .build());

            allocatedDiscount += discountAmount;
            index += 1;
            productQuantities.add(quantity);
            deductionProducts.add(DeductionProductDTO.builder()
                    .productId(productId)
                    .deductionStock(quantity)
                    .orderId(orderMessage.getOrderId())
                    .build());
        }

        insertRow = orderItemMapper.insertOrderItemBatch(orderItemList);
        if (insertRow <= 0) {
            throw new ServiceException(ResultCodeEnum.INSERT_ORDER_ITEM_ERROR);
        }

        insertLocalMessage(
                orderMessage.getUserId(),
                MsgTopic.TOPIC_ORDER_CREATED.getTopic(),
                JSONUtil.toJsonStr(OrderCreatedMessage.builder()
                        .orderId(order.getOrderId())
                        .orderNo(order.getOrderNo())
                        .userId(order.getUserId())
                        .payAmount(payAmount)
                        .payChannel(order.getPayChannel())
                        .receiverName(order.getReceiverName())
                        .receiverPhone(order.getReceiverPhone())
                        .receiverAddress(order.getReceiverAddress())
                        .build()),
                new Date()
        );

        OrderWithProduct cancelMessage = new OrderWithProduct();
        cancelMessage.setOrderId(order.getOrderId());
        cancelMessage.setOrderNo(order.getOrderNo());
        cancelMessage.setUserId(order.getUserId());
        cancelMessage.setPayAmount(order.getPayAmount());
        cancelMessage.setStatus(order.getStatus());
        cancelMessage.setPayChannel(order.getPayChannel());
        cancelMessage.setProductIds(productIds);
        cancelMessage.setProductQuantities(productQuantities);
        cancelMessage.setCouponId(orderMessage.getCouponId());
        cancelMessage.setCouponSegment(orderMessage.getCouponSegment());
        cancelMessage.setCouponToken(orderMessage.getCouponToken());

        insertLocalMessage(
                orderMessage.getUserId(),
                MsgTopic.TOPIC_ORDER_CANCEL.getTopic(),
                JSONUtil.toJsonStr(cancelMessage),
                new Date(System.currentTimeMillis() + 15 * 60 * 1000L)
        );

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (DeductionProductDTO deductionProduct : deductionProducts) {
                    rabbitTemplate.convertAndSend(
                            RabbitEventConstants.EVENT_EXCHANGE,
                            TOPIC_STOCK_DEDUCTION.getTopic(),
                            JSONUtil.toJsonStr(deductionProduct)
                    );
                }
            }
        });
    }

    private void insertLocalMessage(Long userId, String topic, String payload, Date nextRetryTime) {
        Msg msg = Msg.builder()
                .msgId(GeneIdGenerator.generatorId(userId))
                .businessId(GeneIdGenerator.generatorId(userId))
                .topic(topic)
                .payload(payload)
                .status(MsgStatus.PENDING_SENDING.getStatus())
                .retryCount(0)
                .nextRetryTime(nextRetryTime)
                .build();
        int insertRow = msgMapper.insertMsg(msg);
        if (insertRow <= 0) {
            throw new ServiceException(ResultCodeEnum.INSERT_MSG_ERROR);
        }
    }
}
