package com.web3.exchange.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * KYC 认证状态 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "KYC认证状态")
public class KycStatusVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "KYC状态:0=未认证,1=审核中,2=已认证,3=拒绝", example = "1")
    private Integer kycStatus;

    @Schema(description = "KYC等级:0=未认证,1=L1,2=L2,3=L3", example = "0")
    private Integer kycLevel;
}
