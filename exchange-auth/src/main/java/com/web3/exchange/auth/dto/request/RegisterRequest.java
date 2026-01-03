package com.web3.exchange.auth.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

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

    @NotBlank(message = "确认密码不能为空")
    @Schema(description = "确认密码", required = true, example = "Aa123456")
    private String confirmPassword;

    @NotBlank(message = "姓名不能为空")
    @Size(min = 2, max = 20, message = "姓名长度为2-20个字符")
    @Schema(description = "姓名", required = true, example = "张三")
    private String realName;

    @Email(message = "邮箱格式不正确")
    @NotBlank(message = "邮箱不能为空")
    @Schema(description = "邮箱", required = true, example = "zhangsan@example.com")
    private String email;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @NotBlank(message = "手机号不能为空")
    @Schema(description = "手机号", required = true, example = "13800138000")
    private String phone;

    @Schema(description = "头像URL", example = "https://example.com/avatar.jpg")
    private String avatar;

    @Min(value = 0, message = "性别不合法")
    @Max(value = 2, message = "性别不合法")
    @Schema(description = "性别 0:未知 1:男 2:女", example = "1")
    private Integer gender = 0;

    @Past(message = "生日必须是过去的时间")
    @Schema(description = "生日", example = "1990-01-01")
    private LocalDate birthday;

    @Schema(description = "邀请码", example = "INVITE123")
    private String inviteCode;

    @Schema(description = "验证码", example = "1234")
    private String captcha;

    @Schema(description = "验证码UUID", example = "d9b1d7db-4e5e-4e7a-bc8e-91f9b3a3e3a1")
    private String captchaId;

    @Schema(description = "注册IP", example = "192.168.1.1")
    private String registerIp;

    @Schema(description = "注册来源", example = "web")
    private String source = "web";

    @Schema(description = "角色ID列表")
    private List<Long> roleIds;

    @AssertTrue(message = "两次输入的密码不一致")
    public boolean isPasswordMatch() {
        return password != null && password.equals(confirmPassword);
    }
}