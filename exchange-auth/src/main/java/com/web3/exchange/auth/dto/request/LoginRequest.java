package com.web3.exchange.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "登录请求参数")
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度为3-20个字符")
    @Schema(description = "用户名", required = true, example = "admin")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度为6-20个字符")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
            message = "密码必须包含大小写字母和数字")
    @Schema(description = "密码", required = true, example = "Admin123")
    private String password;

    @Schema(description = "验证码", example = "1234")
    private String captcha;

    @Schema(description = "验证码UUID", example = "d9b1d7db-4e5e-4e7a-bc8e-91f9b3a3e3a1")
    private String captchaId;

    @Schema(description = "登录IP", example = "192.168.1.1")
    private String loginIp;

    @Schema(description = "设备标识", example = "web")
    private String device = "web";

    @Schema(description = "设备指纹", example = "fingerprint-123")
    private String deviceFingerprint;

    @Schema(description = "记住我", example = "false")
    private Boolean rememberMe = false;
}