package com.suddenfix.order.domain.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 优惠券领取明细表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 领取记录唯一ID (雪花算法)
     */
    private Long id;

    /**
     * 优惠券ID
     */
    private Long couponId;

    /**
     * 领取用户的ID
     */
    private Long userId;

    /**
     * 领取的Redis分段编号 (如: 0, 1, 2...)
     */
    private Integer segmentIndex;

    /**
     * 优惠券唯一Token (对应 Redis List 弹出的弹药)
     */
    private String couponToken;

    /**
     * 使用状态：0-未使用, 1-已使用, 2-已过期
     */
    private Integer status;

    /**
     * 核销订单ID (使用后回写)
     */
    private Long orderId;

    /**
     * 核销/使用时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date usedTime;

    /**
     * 领取时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date updateTime;
}