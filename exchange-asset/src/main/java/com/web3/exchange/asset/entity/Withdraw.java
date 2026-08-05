package com.web3.exchange.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 提现记录表（t_withdraw）——记录用户出金申请及审核、上链状态。
 * <p>
 * 业务角色：用户申请提现后落一条记录，经历审核与上链；实际到账金额
 * realAmount = amount - fee（fee 为平台手续费，均以最小单位整数存储）。
 * request_id 为申请时的幂等号（uk_request_id），防重复申请/重复处理。
 * </p>
 * <p>
 * 状态流转 status：0=待审核 → 1=审核中 → 2=处理中(已冻结上链) → 3=成功；
 * 4=拒绝；5=失败回滚。freezeLedgerId 记录提现冻结时生成的流水ID，用于追溯。
 * </p>
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
}
