package com.suddenfix.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayCreatedMessage {

    private Long orderId;

    private Long userId;

    private Long payId;

    private String outTradeNo;

    private Integer payChannel;

    private Long amount;
}
