package com.suddenfix.user.domain.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户实名认证信息实体
 * 对应逻辑表：t_user_auth
 * 注意：该表的 realName 和 idCard 在数据库层通过 ShardingSphere 进行 AES 加密存储
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAuth implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     * 对应分片键：user_id
     */
    private Long userId;

    /**
     * 真实姓名
     * 在数据库中以密文存储，Java 层由 ShardingSphere 自动加解密，透明处理明文
     */
    private String realName;

    /**
     * 身份证号
     * 在数据库中以密文存储，Java 层透明处理
     */
    private String idCard;

    /**
     * 认证状态
     * 0: 未认证, 1: 审核中, 2: 已通过, 3: 审核失败
     */
    private Integer authStatus;

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