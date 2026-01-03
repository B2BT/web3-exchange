package com.web3.exchange.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.*;

@Data
@Schema(description = "重置密码请求")
public class ResetPasswordRequest {

    @NotBlank(message = "用户名或邮箱不能为空")
    @Schema(description = "用户名或邮箱", required = true, example = "admin@example.com")
    private String usernameOrEmail;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度为6-20个字符")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
            message = "密码必须包含大小写字母和数字")
    @Schema(description = "新密码", required = true, example = "NewPass123")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    @Schema(description = "确认新密码", required = true, example = "NewPass123")
    private String confirmPassword;

    @NotBlank(message = "验证码不能为空")
    @Schema(description = "验证码", required = true, example = "123456")
    private String code;

    @Schema(description = "验证码类型", example = "email")
    private String codeType = "email";

    @AssertTrue(message = "两次输入的密码不一致")
    public boolean isPasswordMatch() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }

    public boolean isEmail() {
        if (usernameOrEmail == null) {
            return false;
        }
        String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
        return usernameOrEmail.matches(emailRegex);
    }
}