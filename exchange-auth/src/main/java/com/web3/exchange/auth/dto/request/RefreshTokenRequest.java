package com.web3.exchange.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新令牌请求参数
 */
@Data
@Schema(description = "刷新令牌请求参数")
public class RefreshTokenRequest {

    @NotBlank(message = "刷新令牌不能为空")
    @Schema(description = "刷新令牌", required = true,
            example = "eyJhbG...VCJ9...")
    private String refreshToken;
}
