package com.suddenfix.user.domain.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户收货/服务地址实体类
 * 对应逻辑表：t_user_address
 * 物理分布：suddenfix_user_0/1.t_user_address_0~15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAddress implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 地址主键 ID
     * 使用 ShardingSphere 雪花算法生成，全局唯一
     */
    private Long id;

    /**
     * 用户 ID
     * 分片键
     */
    private Long userId;

    /**
     * 收货人姓名
     */
    private String consignee;

    /**
     * 收货人联系电话
     */
    private String phone;

    /**
     * 省份
     */
    private String province;

    /**
     * 城市
     */
    private String city;

    /**
     * 区/县
     */
    private String district;

    /**
     * 详细地址（街道、门牌号等）
     */
    private String detailAddress;

    /**
     * 是否为默认地址
     * 1: 是, 0: 否
     */
    private Integer isDefault;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}