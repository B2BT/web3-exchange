package com.web3.exchange.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 交易 API 密钥（t_api_key）。
 * <p>用户级 OpenAPI 凭证：apiKey(公钥标识) + secretKey(私钥，AES-GCM 密文存储，明文仅创建时返回一次)。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_api_key")
public class ApiKey extends BaseEntity {
    /** 所属用户ID */
    private Long userId;
    /** 公钥标识（全局唯一，W3- 前缀） */
    private String apiKey;
    /** 私钥密文（AES-GCM 加密存储） */
    private String secretKey;
    /** 备注 */
    private String label;
    /** 权限：READ/TRADE/WITHDRAW（逗号分隔可组合） */
    private String permission;
    /** 状态：1=启用 0=停用 */
    private Integer status;
    /** 最近使用时间 */
    private LocalDateTime lastUsedAt;
}
