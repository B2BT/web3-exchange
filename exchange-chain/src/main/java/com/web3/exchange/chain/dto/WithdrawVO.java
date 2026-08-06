package com.web3.exchange.chain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 提现记录视图（金额=最小单位 long）。
 */
@Data
@Schema(description = "提现记录视图")
public class WithdrawVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "提现记录ID")
    private Long id;
    @Schema(description = "幂等请求号")
    private String requestId;
    @Schema(description = "用户ID")
    private Long userId;
    @Schema(description = "币种符号")
    private String symbol;
    @Schema(description = "链编码")
    private String chainCode;
    @Schema(description = "提现目标地址")
    private String toAddress;
    @Schema(description = "提现金额(最小单位)")
    private Long amount;
    @Schema(description = "手续费(最小单位)")
    private Long fee;
    @Schema(description = "实际到账=amount-fee")
    private Long realAmount;
    @Schema(description = "状态:0=待审核,1=审核中,2=处理中,3=成功,4=拒绝,5=失败回滚")
    private Integer status;
    @Schema(description = "审核人")
    private String auditBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auditTime;
    @Schema(description = "审核备注")
    private String auditRemark;
    @Schema(description = "冻结流水ID")
    private Long freezeLedgerId;
    @Schema(description = "上链哈希")
    private String txHash;
    @Schema(description = "失败原因")
    private String failReason;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
