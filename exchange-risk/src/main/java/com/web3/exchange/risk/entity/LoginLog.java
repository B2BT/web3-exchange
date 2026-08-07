package com.web3.exchange.risk.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 登录日志表（t_login_log）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_risk_login_log")
public class LoginLog extends BaseEntity {
    /** 用户ID */
    private Long userId;
    /** 用户名 */
    private String username;
    /** 登录IP */
    private String ip;
    /** UA */
    private String userAgent;
    /** 设备(粗略) */
    private String device;
    /** 结果:0=成功,1=失败 */
    private Integer result;
    /** 风险:0=正常,1=异常(异地/新设备) */
    private Integer risk;
}
