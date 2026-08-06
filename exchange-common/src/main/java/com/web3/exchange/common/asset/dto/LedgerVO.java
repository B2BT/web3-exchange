package com.web3.exchange.common.asset.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 资金流水视图（跨模块复用，资金操作返回/流水查询结果）。
 * <p><b>金额单位 = 最小单位</b>。amount 恒正；before/after 为变动前后余额，用于对账审计；
 * requestId 为幂等键；direction 见 Direction 常量，bizType 见 BizType 常量。</p>
 */
@Data
@Schema(description = "资金流水视图")
public class LedgerVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "流水ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @Schema(description = "幂等请求号")
    private String requestId;
    @Schema(description = "用户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    @Schema(description = "账户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;
    @Schema(description = "币种ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long coinId;
    @Schema(description = "币种符号")
    private String symbol;
    @Schema(description = "业务类型:FREEZE/UNFREEZE/TRANSFER_IN/TRANSFER_OUT/DEPOSIT/WITHDRAW/FEE/REBATE")
    private String bizType;
    @Schema(description = "资金方向:1=IN 2=OUT 3=FROZEN 4=UNFROZEN 5=FROZEN_OUT")
    private Integer direction;
    @Schema(description = "变动金额(最小单位,恒正)")
    private Long amount;
    @Schema(description = "变动前可用余额")
    private Long beforeAvailable;
    @Schema(description = "变动后可用余额")
    private Long afterAvailable;
    @Schema(description = "变动前冻结余额")
    private Long beforeFrozen;
    @Schema(description = "变动后冻结余额")
    private Long afterFrozen;
    @Schema(description = "业务单号")
    private String refNo;
    @Schema(description = "状态:1=成功")
    private Integer status;
    @Schema(description = "备注")
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
