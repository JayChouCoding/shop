package com.suddenfix.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaidMessage {

    private Long orderId;

    private Long userId;

    private Long payAmount;

    private String outTradeNo;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;
}
