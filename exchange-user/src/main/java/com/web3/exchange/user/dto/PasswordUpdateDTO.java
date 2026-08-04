package com.web3.exchange.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新密码请求
 * <p>供 auth 服务修改/重置密码(P3-B)通过 Feign 调用。</p>
 */
@Data
@Schema(description = "更新密码请求")
public class PasswordUpdateDTO {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度为6-20个字符")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
            message = "密码必须包含大小写字母和数字")
    @Schema(description = "新密码（明文，内部 BCrypt 加密）", requiredMode = Schema.RequiredMode.REQUIRED, example = "NewPass123")
    private String newPassword;
}
