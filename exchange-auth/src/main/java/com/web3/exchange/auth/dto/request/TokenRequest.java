package com.web3.exchange.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
@Schema(description = "Token相关请求")
public class TokenRequest {

    @NotBlank(message = "Refresh Token不能为空")
    @Schema(description = "刷新令牌", required = true,
            example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String refreshToken;

    @Schema(description = "设备指纹", example = "fingerprint-123")
    private String deviceFingerprint;

    @Schema(description = "客户端ID", example = "web-client")
    private String clientId = "default";
}