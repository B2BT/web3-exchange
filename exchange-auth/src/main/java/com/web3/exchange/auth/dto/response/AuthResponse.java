package com.web3.exchange.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "认证响应")
public class AuthResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank
    @Schema(description = "访问令牌", required = true,
            example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;

    @Schema(description = "刷新令牌",
            example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String refreshToken;

    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "过期时间(秒)", example = "3600")
    private Long expiresIn;

    @Schema(description = "用户信息")
    private UserInfoResponse userInfo;

    @Schema(description = "权限列表")
    private List<String> authorities;

    @Schema(description = "角色列表")
    private List<String> roles;

    @Schema(description = "是否需要修改密码", example = "false")
    private Boolean needChangePassword = false;

    @Schema(description = "是否是首次登录", example = "true")
    private Boolean firstLogin = false;

    @Schema(description = "会话ID", example = "session-123456")
    private String sessionId;

    @Schema(description = "登录时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime loginTime;

    @Schema(description = "Token到期时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime tokenExpireTime;

    // Builder 方法
    public static AuthResponseBuilder builder() {
        return new AuthResponseBuilder();
    }

    public static AuthResponse of(String accessToken, UserInfoResponse userInfo) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .userInfo(userInfo)
                .build();
    }


    public static AuthResponse of(String accessToken, String refreshToken,
                                  UserInfoResponse userInfo, List<String> authorities) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userInfo(userInfo)
                .authorities(authorities)
                .expiresIn(3600L)
                .build();
    }
}
