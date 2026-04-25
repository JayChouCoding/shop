package com.suddenfix.shipping.domain.dto;

import lombok.Data;

@Data
public class ShipOrderRequest {
    private Long orderId;
    private Long userId;
    private String logisticsNo;
    private String expressCompany;
    private String remark;
}
