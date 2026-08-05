package com.web3.exchange.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 提现记录表
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
