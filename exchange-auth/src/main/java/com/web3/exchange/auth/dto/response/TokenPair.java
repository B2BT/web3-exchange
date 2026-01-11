package com.web3.exchange.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 双令牌对
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "令牌对")
public class TokenPair {
    @Schema(description = "访问令牌")
    private String accessToken;

    @Schema(description = "刷新令牌")
    private String refreshToken;

    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "访问令牌过期时间(秒)")
    private Long accessTokenExpiresIn;

    @Schema(description = "刷新令牌过期时间(秒)")
    private Long refreshTokenExpiresIn;

    @Schema(description = "是否需要刷新访问令牌")
    private Boolean needRefresh = false;
}
