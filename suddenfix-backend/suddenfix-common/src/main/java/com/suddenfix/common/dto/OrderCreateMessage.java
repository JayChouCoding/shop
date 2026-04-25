package com.suddenfix.common.dto;

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
public class OrderCreateMessage {

    private Long orderId;

    private Long userId;

    private Map<Long, List<Long>> products;

    private Map<Long, String> productNames;

    private Long totalAmount;

    private Long freightAmount;

    private Long discountAmount;

    private Long payAmount;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private String remark;

    private Integer payChannel;

    private Long couponId;

    private Integer couponSegment;

    private String couponToken;
}
