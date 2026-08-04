package com.web3.exchange.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求
 * <p>
 * 该接口后续会被 auth 服务注册接口(P3-B)通过 Feign 调用，入参需保持稳定。
 */
@Data
@Schema(description = "用户注册请求")
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度为3-20个字符")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$",
            message = "用户名必须以字母开头，只能包含字母、数字、下划线")
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "zhangsan")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度为6-20个字符")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
            message = "密码必须包含大小写字母和数字")
    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "Aa123456")
    private String password;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱", requiredMode = Schema.RequiredMode.REQUIRED, example = "zhangsan@example.com")
    private String email;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800138000")
    private String phone;

    @Schema(description = "昵称", example = "张三")
    private String nickname;

    @Schema(description = "邀请码（可选）", example = "INVITE123")
    private String inviteCode;

    @Schema(description = "注册来源:web/app/API（可选）", example = "web")
    private String registerSource = "web";

    @Schema(description = "注册IP（可选）", example = "127.0.0.1")
    private String registerIp;
}
