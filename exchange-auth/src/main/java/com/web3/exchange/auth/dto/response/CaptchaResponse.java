package com.web3.exchange.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "验证码响应")
public class CaptchaResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "验证码UUID", required = true,
            example = "d9b1d7db-4e5e-4e7a-bc8e-91f9b3a3e3a1")
    private String captchaId;

    @Schema(description = "验证码图片Base64", required = true,
            example = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...")
    private String captchaImage;

    @Schema(description = "验证码文本（仅测试环境返回）",
            example = "1234")
    private String captchaText;

    @Schema(description = "验证码类型", example = "math")
    private String type = "image";

    @Schema(description = "过期时间(秒)", example = "300")
    private Integer expireSeconds = 300;

    @Schema(description = "生成时间戳", example = "1672502400000")
    private Long timestamp;

    public static CaptchaResponse of(String captchaId, String captchaImage) {
        return CaptchaResponse.builder()
                .captchaId(captchaId)
                .captchaImage(captchaImage)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}