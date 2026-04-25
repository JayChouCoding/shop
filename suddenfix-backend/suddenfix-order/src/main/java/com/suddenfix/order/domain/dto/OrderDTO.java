package com.suddenfix.order.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {

    private Long orderId;

    private Long userId;

    private String idempotentKey;

    // map中key存商品ID,list中索引0存购买商品的数量、索引1存购买商品的单价
    private Map<Long,List<Long>> products;

    private Map<Long,String> productNames;

    private Long freight;

    private Long discountAmount;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private String remark;

    private Integer payChannel;

    private Long couponId;

    private Integer couponSegment;

    private String couponToken;
}
