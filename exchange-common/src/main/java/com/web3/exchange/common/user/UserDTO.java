package com.web3.exchange.common.user;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * 用户基本信息DTO
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户ID（雪花 Long，序列化为 String 避免 JS 精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private Integer status;
    private Long deptId;
    private Long tenantId;
    private List<String> roles;
    private List<String> permissions;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
