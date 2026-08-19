package com.web3.exchange.chain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 提现审核请求。
 */
@Data
@Schema(description = "提现审核请求")
public class AuditRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "是否通过:true=通过(进入下一步:初审通过待复核/复核通过冻结上链),false=拒绝", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean approve;
    @Schema(description = "审核备注")
    private String remark;
    @Schema(description = "审核级别:1=初审(默认),2=复核(需与初审为不同审核员)")
    private Integer level;
    @Schema(description = "审核员标识(必填, 复核须与初审不同)")
    private String reviewer;
}
