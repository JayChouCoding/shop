package com.suddenfix.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayRollbackMessage {

    private Long orderId;

    private String outTradeNo;

    private Long refundAmount;

    private String refundNo;
}
