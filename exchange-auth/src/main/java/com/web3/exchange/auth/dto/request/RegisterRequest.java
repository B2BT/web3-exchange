package com.web3.exchange.auth.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.*;

/**
 * 用户注册请求（auth 服务入口，最终经 Feign 调 user 服务注册）
 * <p>字段与 user 服务 RegisterDTO 对齐：username/password/email/phone/nickname/inviteCode。</p>
 */
@Data
@Schema(description = "用户注册请求")
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度为3-20个字符")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$",
            message = "用户名必须以字母开头，只能包含字母、数字、下划线")
    @Schema(description = "用户名", required = true, example = "zhangsan")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度为6-20个字符")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
            message = "密码必须包含大小写字母和数字")
    @Schema(description = "密码", required = true, example = "Aa123456")
    private String password;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱", required = true, example = "zhangsan@example.com")
    private String email;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号", required = true, example = "13800138000")
    private String phone;

    @Schema(description = "昵称（可选）", example = "张三")
    private String nickname;

    @Schema(description = "邀请码（可选）", example = "INVITE123")
    private String inviteCode;

    @Schema(description = "验证码", example = "8")
    private String captcha;

    @Schema(description = "验证码UUID", example = "d9b1d7db-4e5e-4e7a-bc8e-91f9b3a3e3a1")
    private String captchaId;

    @Schema(description = "注册IP", example = "192.168.1.1")
    private String registerIp;

    @Schema(description = "注册来源", example = "web")
    private String source = "web";
}
