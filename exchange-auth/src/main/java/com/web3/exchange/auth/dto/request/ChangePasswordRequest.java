package com.web3.exchange.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.*;

@Data
@Schema(description = "修改密码请求")
public class ChangePasswordRequest {

    @NotBlank(message = "原密码不能为空")
    @Schema(description = "原密码", required = true, example = "OldPass123")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度为6-20个字符")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
            message = "密码必须包含大小写字母和数字")
    @Schema(description = "新密码", required = true, example = "NewPass123")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    @Schema(description = "确认新密码", required = true, example = "NewPass123")
    private String confirmPassword;

    @Schema(description = "验证码", example = "1234")
    private String captcha;

    @Schema(description = "验证码UUID", example = "d9b1d7db-4e5e-4e7a-bc8e-91f9b3a3e3a1")
    private String captchaId;

    @AssertTrue(message = "两次输入的新密码不一致")
    public boolean isNewPasswordMatch() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }

    @AssertFalse(message = "新密码不能与原密码相同")
    public boolean isSameAsOldPassword() {
        return newPassword != null && newPassword.equals(oldPassword);
    }
}