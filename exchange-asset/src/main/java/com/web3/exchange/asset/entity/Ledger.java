package com.web3.exchange.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 资产流水表（append-only 不可变）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_asset_ledger")
public class Ledger extends BaseEntity {
    /** 幂等请求号(业务方生成,全局唯一) */
    private String requestId;
    /** 用户ID */
    private Long userId;
    /** 账户ID(关联t_wallet_account) */
    private Long accountId;
    /** 币种ID */
    private Long coinId;
    /** 币种符号 */
    private String symbol;
    /** 业务类型:FREEZE/UNFREEZE/TRANSFER_IN/TRANSFER_OUT/DEPOSIT/WITHDRAW/FEE/REBATE */
    private String bizType;
    /** 资金方向:1=IN 2=OUT 3=FROZEN 4=UNFROZEN 5=FROZEN_OUT */
    private Integer direction;
    /** 变动金额(最小单位,恒正) */
    private Long amount;
    /** 变动前可用余额 */
    private Long beforeAvailable;
    /** 变动后可用余额 */
    private Long afterAvailable;
    /** 变动前冻结余额 */
    private Long beforeFrozen;
    /** 变动后冻结余额 */
    private Long afterFrozen;
    /** 业务单号(orderId/withdrawId/depositId) */
    private String refNo;
    /** 状态:0=处理中,1=成功,2=失败,3=回滚 */
    private Integer status;
    /** 备注 */
    private String remark;
}
