package com.web3.exchange.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * KYC 实名认证提交请求
 */
@Data
@Schema(description = "KYC实名认证提交请求")
public class KYCSubmitDTO {

    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 50, message = "真实姓名长度不能超过50个字符")
    @Schema(description = "真实姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    private String realName;

    @NotBlank(message = "证件类型不能为空")
    @Schema(description = "证件类型:ID_CARD/PASSPORT", requiredMode = Schema.RequiredMode.REQUIRED, example = "ID_CARD")
    private String idCardType;

    @NotBlank(message = "证件号码不能为空")
    @Size(max = 50, message = "证件号码长度不能超过50个字符")
    @Schema(description = "证件号码", requiredMode = Schema.RequiredMode.REQUIRED, example = "110101199001011234")
    private String idCardNo;

    @Size(max = 500, message = "证件正面照URL长度不能超过500个字符")
    @Schema(description = "证件正面照URL", example = "https://example.com/idcard-front.jpg")
    private String idCardFront;

    @Size(max = 500, message = "证件背面照URL长度不能超过500个字符")
    @Schema(description = "证件背面照URL", example = "https://example.com/idcard-back.jpg")
    private String idCardBack;
}
