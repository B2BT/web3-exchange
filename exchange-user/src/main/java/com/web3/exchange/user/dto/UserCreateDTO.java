package com.web3.exchange.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.Date;

@Data
@Schema(description = "创建用户请求")
public class UserCreateDTO {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度为3-20个字符")
    @Schema(description = "用户名",requiredMode = Schema.RequiredMode.REQUIRED, example = "example")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度为6-20个字符")
    @Schema(description = "密码",requiredMode = Schema.RequiredMode.REQUIRED, example = "asdfsaf")
    private String password;

    @NotBlank(message = "姓名不能为空")
    @Schema(description = "姓名",requiredMode = Schema.RequiredMode.REQUIRED, example = "asdfsaf")
    private String realName;

    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱",requiredMode = Schema.RequiredMode.REQUIRED, example = "asdfsaf")
    private String email;

    @Pattern(regexp = "^1[3-9]\\\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号",requiredMode = Schema.RequiredMode.REQUIRED, example = "asdfsaf")
    private String phone;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "生日",requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "asdfsaf")
    private LocalDate birthday;

}
