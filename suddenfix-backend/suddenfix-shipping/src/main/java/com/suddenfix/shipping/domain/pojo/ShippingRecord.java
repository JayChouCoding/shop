package com.suddenfix.shipping.domain.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingRecord {
    private Long shippingId;
    private Long orderId;
    private Long userId;
    private String logisticsNo;
    private String expressCompany;
    private Integer shippingStatus;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date shipTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date signTime;
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
