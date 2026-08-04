package com.web3.exchange.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户资料修改请求
 * <p>
 * 仅允许修改 nickname/email/phone/avatar/realName，不允许修改 username/password。
 */
@Data
@Schema(description = "用户资料修改请求")
public class UpdateProfileDTO {

    @Size(max = 50, message = "昵称长度不能超过50个字符")
    @Schema(description = "昵称", example = "张三")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Size(max = 500, message = "头像URL长度不能超过500个字符")
    @Schema(description = "头像URL", example = "https://example.com/avatar.jpg")
    private String avatar;

    @Size(max = 50, message = "真实姓名长度不能超过50个字符")
    @Schema(description = "真实姓名", example = "张三")
    private String realName;
}
