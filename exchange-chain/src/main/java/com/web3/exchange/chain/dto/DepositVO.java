package com.web3.exchange.chain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 充值记录视图（金额=最小单位 long）。
 */
@Data
@Schema(description = "充值记录视图")
public class DepositVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "充值记录ID")
    private Long id;
    @Schema(description = "用户ID")
    private Long userId;
    @Schema(description = "币种符号")
    private String symbol;
    @Schema(description = "链编码")
    private String chainCode;
    @Schema(description = "来源地址")
    private String fromAddress;
    @Schema(description = "充值目标地址")
    private String toAddress;
    @Schema(description = "充值金额(最小单位)")
    private Long amount;
    @Schema(description = "网络手续费(最小单位)")
    private Long fee;
    @Schema(description = "链上交易哈希")
    private String txHash;
    @Schema(description = "所在区块高度")
    private Long blockHeight;
    @Schema(description = "已确认数")
    private Integer confirmations;
    @Schema(description = "入账所需确认数")
    private Integer requiredConfirmations;
    @Schema(description = "入账流水ID")
    private Long ledgerId;
    @Schema(description = "状态:0=监听中,1=待确认,2=已入账,3=失败")
    private Integer status;
    @Schema(description = "备注")
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
