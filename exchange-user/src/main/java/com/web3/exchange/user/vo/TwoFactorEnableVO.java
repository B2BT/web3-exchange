package com.web3.exchange.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 2FA 开启结果 VO
 * <p>
 * 返回 base32 格式的密钥，前端据此生成 Google Authenticator 二维码。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "2FA开启结果")
public class TwoFactorEnableVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "2FA密钥（base32，供生成二维码）", example = "JBSWY3DPEHPK3PXP")
    private String secret;

    @Schema(description = "是否已开启", example = "true")
    private Boolean enabled;
}
