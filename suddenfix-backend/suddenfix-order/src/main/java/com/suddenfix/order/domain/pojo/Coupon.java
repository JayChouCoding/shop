package com.suddenfix.order.domain.pojo;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 优惠券活动配置表实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coupon implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 优惠券ID (主键)
     */
    private Long id;

    /**
     * 优惠券名称 (例如：满100减50)
     */
    private String name;

    /**
     * 优惠金额
     */
    private BigDecimal amount;

    /**
     * 使用门槛 (0表示无门槛)
     */
    private BigDecimal minPoint;

    /**
     * 总库存量
     */
    private Integer totalStock;

    /**
     * Redis分段数量 (默认10段)
     */
    private Integer segmentCount;

    /**
     * 状态：0-未开始(未预热), 1-进行中(已预热READY), 2-已结束
     */
    private Integer status;

    /**
     * 活动开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date startTime;

    /**
     * 活动结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date endTime;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date updateTime;

    /**
     * 逻辑删除：0-未删除，1-已删除
     */
    private Integer isDeleted;
}