package com.web3.exchange.risk.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.risk.dto.LoginRecordRequest;
import com.web3.exchange.risk.dto.OrderRiskRequest;
import com.web3.exchange.risk.dto.OrderRiskResult;
import com.web3.exchange.risk.dto.WithdrawRiskRequest;
import com.web3.exchange.risk.dto.WithdrawRiskResult;
import com.web3.exchange.risk.dto.WithdrawVerifyRequest;
import com.web3.exchange.risk.entity.AntiPhishing;
import com.web3.exchange.risk.entity.LoginLog;

import java.util.List;

/**
 * 风控服务。
 */
public interface RiskService {

    /** 风控规则列表（启用） */
    List<com.web3.exchange.risk.entity.RiskRule> listRules();

    /** 下单风控前置校验 */
    OrderRiskResult preCheckOrder(OrderRiskRequest req);

    /** 设置反钓鱼码 */
    AntiPhishing setPhishing(Long userId, String phrase);

    /** 查询反钓鱼码 */
    AntiPhishing getPhishing(Long userId);

    /** 提现前置：反钓鱼码校验 + 生成二次验证码 */
    WithdrawRiskResult preCheckWithdraw(WithdrawRiskRequest req);

    /** 提现二次验证码校验 */
    boolean verifyWithdraw(WithdrawVerifyRequest req);

    /** 记录登录日志 */
    LoginLog recordLogin(LoginRecordRequest req);

    /** 我的登录日志分页 */
    Page<LoginLog> pageLoginLogs(Long userId, int page, int size);
}
