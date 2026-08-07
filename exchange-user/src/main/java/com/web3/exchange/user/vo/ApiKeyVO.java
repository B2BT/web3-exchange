package com.web3.exchange.user.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * API 密钥视图。列表时 secretKey 脱敏，仅创建响应回传明文一次。
 */
@Data
public class ApiKeyVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /** 公钥标识 */
    private String apiKey;
    /** 私钥（列表脱敏为 ***，创建响应为明文一次） */
    private String secretKey;
    private String label;
    private String permission;
    private Integer status;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createTime;
}
