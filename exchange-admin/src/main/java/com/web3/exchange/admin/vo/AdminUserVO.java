package com.web3.exchange.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理平台用户列表 VO（不含密码等敏感字段）。
 */
@Data
@Schema(description = "后台用户视图")
public class AdminUserVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID（雪花，String 防精度丢失）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @Schema(description = "用户名")
    private String username;
    @Schema(description = "邮箱")
    private String email;
    @Schema(description = "手机号")
    private String phone;
    @Schema(description = "角色:USER普通/ADMIN管理员")
    private String role;
    @Schema(description = "账户状态:0=禁用,1=正常,2=锁定,3=冻结")
    private Integer status;
    @Schema(description = "注册时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registerTime;
}
