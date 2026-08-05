package com.web3.exchange.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 资产流水表（t_asset_ledger）——资金变动的不可变审计账本。
 * <p>
 * 业务角色：每一笔资金变动（冻结/解冻/过户/充值/提现…）都写入一条流水，
 * 记录变动前后余额（before/after），用于对账与审计。本表为 <b>append-only</b>：
 * 业务上禁止 UPDATE/DELETE，只允许新增，以保证账本可追溯、可复核。
 * </p>
 * <p>
 * 幂等设计：request_id 由调用方生成并全局唯一（唯一索引 uk_request_id 兜底），
 * 同一 request_id 重复请求直接返回首次结果，杜绝重复扣减/入账；同时
 * uk_biz_no(user_id, biz_type, ref_no) 在业务单号维度防同单重复入账。
 * direction 为资金方向（见 Direction 常量），biz_type 为业务类型（见 BizType 常量），
 * amount 恒为正数（最小单位）。
 * </p>
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
