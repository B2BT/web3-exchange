package com.web3.exchange.chain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 提现记录表（t_withdraw）——记录用户出金申请及审核、上链状态（读 asset 库既有表，不建新表）。
 * <p>用户申请提现后落一条记录，经历审核与上链；实际到账 realAmount = amount - fee。
 * request_id 为申请时的幂等号（uk_request_id）。</p>
 * <p>状态流转 status：0=待审核 → 1=审核中 → 2=处理中(已冻结上链) → 3=成功；4=拒绝；5=失败回滚。
 * freezeLedgerId 记录提现冻结流水ID，txHash 记录上链哈希。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_withdraw")
public class Withdraw extends BaseEntity {
    /** 幂等请求号(申请时生成) */
    private String requestId;
    /** 用户ID */
    private Long userId;
    /** 币种ID */
    private Long coinId;
    /** 币种符号 */
    private String symbol;
    /** 链编码 */
    private String chainCode;
    /** 提现目标地址 */
    private String toAddress;
    /** 提现金额(最小单位) */
    private Long amount;
    /** NFT代币ID(ERC-721/1155提现指定; ERC-20为空) */
    private String tokenId;
    /** 手续费(最小单位) */
    private Long fee;
    /** 实际到账=amount-fee(最小单位) */
    private Long realAmount;
    /** 状态:0=待审核,1=审核中,2=处理中,3=成功,4=拒绝,5=失败回滚 */
    private Integer status;
    /** 审核人 */
    private String auditBy;
    /** 审核时间 */
    private LocalDateTime auditTime;
    /** 审核备注 */
    private String auditRemark;
    /** 冻结流水ID */
    private Long freezeLedgerId;
    /** 上链哈希 */
    private String txHash;
    /** 失败原因 */
    private String failReason;
    /** 签名模式:1=热钱包(小额在线签名),2=冷钱包(大额离线签名) */
    private Integer signMode;
    /** 冷钱包待签名交易(unsigned tx hex) */
    private String coldTxData;
    /** 待签交易签名哈希(供离线核对) */
    private String coldSignHash;
    /** 离线签名后的 raw tx(hex) */
    private String coldSignedRaw;
    /** 已收集签名数(多签确认) */
    private Integer coldSignCount;
    /** 需签名数阈值(多签确认) */
    private Integer coldRequiredSigns;
    /** 审核单ID(若走多签审核流程) */
    private Long approvalId;
}
