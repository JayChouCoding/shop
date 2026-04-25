package com.suddenfix.product.domain.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockFlow {

    private Long flowId;
    private Long productId;
    private Long orderId;
    private String businessType;
    private Integer changeType;
    private Integer changeAmount;
    private Integer beforeStock;
    private Integer afterStock;
    private String traceId;
    private String operator;
    private String remark;
    private Date createTime;
}
