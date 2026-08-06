package com.web3.exchange.chain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 充值记录表（t_deposit）——记录每笔链上充值交易及入账状态（读 asset 库既有表，不建新表）。
 * <p>chain 扫描链上交易命中用户充币地址后先落一条充值记录，待确认数达标调用 asset credit 入账。
 * tx_hash 全局唯一（uk_tx_hash）保证同一笔链上交易只能入账一次；request_id 为入账时的幂等号。</p>
 * <p>状态流转 status：0=监听中 → 1=待确认 → 2=已入账；3=失败。ledger_id 关联入账流水。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_deposit")
public class Deposit extends BaseEntity {
    /** 幂等请求号(入账时生成) */
    private String requestId;
    /** 用户ID */
    private Long userId;
    /** 币种ID */
    private Long coinId;
    /** 币种符号 */
    private String symbol;
    /** 链编码 */
    private String chainCode;
    /** 来源地址 */
    private String fromAddress;
    /** 充值目标地址 */
    private String toAddress;
    /** 充值金额(最小单位) */
    private Long amount;
    /** 网络手续费(最小单位) */
    private Long fee;
    /** 链上交易哈希 */
    private String txHash;
    /** 所在区块高度 */
    private Long blockHeight;
    /** 已确认数 */
    private Integer confirmations;
    /** 入账所需确认数 */
    private Integer requiredConfirmations;
    /** 入账流水ID(关联t_asset_ledger) */
    private Long ledgerId;
    /** 状态:0=监听中,1=待确认,2=已入账,3=失败 */
    private Integer status;
    /** 备注 */
    private String remark;
}
