package com.web3.exchange.risk.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 提现二次验证记录表（t_withdraw_verify）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_withdraw_verify")
public class WithdrawVerify extends BaseEntity {
    /** 用户ID */
    private Long userId;
    /** 提现ID */
    private Long withdrawId;
    /** 验证码哈希 */
    private String verifyCodeHash;
    /** 渠道:EMAIL/SMS */
    private String channel;
    /** 状态:0=待验证,1=已通过 */
    private Integer status;
    /** 过期时间 */
    private LocalDateTime expireTime;
}
