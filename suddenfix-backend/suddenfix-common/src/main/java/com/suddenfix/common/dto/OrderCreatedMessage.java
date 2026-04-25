package com.suddenfix.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedMessage {

    private Long orderId;

    private String orderNo;

    private Long userId;

    private Long payAmount;

    private Integer payChannel;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;
}
