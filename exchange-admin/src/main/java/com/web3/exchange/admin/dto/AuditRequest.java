package com.web3.exchange.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 后台提现审核请求（对外 body 用 {@code approved}，与 chain 域 {@code AuditRequest.approve} 语义一致）。
 */
@Data
@Schema(description = "提现审核请求")
public class AuditRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "是否通过:true=通过(冻结上链),false=拒绝", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean approved;
    @Schema(description = "审核备注")
    private String remark;
}
