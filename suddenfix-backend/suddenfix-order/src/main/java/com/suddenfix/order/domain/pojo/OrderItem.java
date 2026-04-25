package com.suddenfix.order.domain.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderItem {

    private Long itemId;

    private Long orderId;

    private Long userId;

    private Long productId;

    private String productName;

    private Long price;

    private Long quantity;

    private Long totalAmount;

    private Long discountAmount;

    private Long realPayAmount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
