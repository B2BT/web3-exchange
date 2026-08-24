package com.web3.exchange.risk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.common.exception.BusinessException;
import com.web3.exchange.risk.config.RiskProperties;
import com.web3.exchange.risk.dto.LoginRecordRequest;
import com.web3.exchange.risk.dto.OrderRiskRequest;
import com.web3.exchange.risk.dto.OrderRiskResult;
import com.web3.exchange.risk.dto.WithdrawRiskRequest;
import com.web3.exchange.risk.dto.WithdrawRiskResult;
import com.web3.exchange.risk.dto.WithdrawVerifyRequest;
import com.web3.exchange.risk.entity.AntiPhishing;
import com.web3.exchange.risk.entity.LoginLog;
import com.web3.exchange.risk.entity.RiskRule;
import com.web3.exchange.risk.entity.WithdrawVerify;
import com.web3.exchange.risk.entity.AmlBlacklist;
import com.web3.exchange.risk.mapper.AntiPhishingMapper;
import com.web3.exchange.risk.mapper.LoginLogMapper;
import com.web3.exchange.risk.mapper.RiskRuleMapper;
import com.web3.exchange.risk.mapper.AmlBlacklistMapper;
import com.web3.exchange.risk.mapper.WithdrawVerifyMapper;
import com.web3.exchange.risk.service.RiskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 风控服务实现。
 */
@Slf4j
@Service
public class RiskServiceImpl implements RiskService {

    private final RiskRuleMapper ruleMapper;
    private final AntiPhishingMapper phishingMapper;
    private final LoginLogMapper loginLogMapper;
    private final WithdrawVerifyMapper verifyMapper;
    private final AmlBlacklistMapper amlBlacklistMapper;
    private final RiskProperties props;

    public RiskServiceImpl(RiskRuleMapper ruleMapper, AntiPhishingMapper phishingMapper,
                           LoginLogMapper loginLogMapper, WithdrawVerifyMapper verifyMapper,
                           AmlBlacklistMapper amlBlacklistMapper, RiskProperties props) {
        this.ruleMapper = ruleMapper;
        this.phishingMapper = phishingMapper;
        this.loginLogMapper = loginLogMapper;
        this.verifyMapper = verifyMapper;
        this.amlBlacklistMapper = amlBlacklistMapper;
        this.props = props;
    }

    @Override
    public List<RiskRule> listRules() {
        return ruleMapper.selectList(new LambdaQueryWrapper<RiskRule>()
                .eq(RiskRule::getStatus, 1)
                .orderByAsc(RiskRule::getId));
    }

    @Override
    public OrderRiskResult preCheckOrder(OrderRiskRequest req) {
        // 1. 单笔金额上限
        Long amount = orderAmount(req);
        RiskRule amountRule = enabledRule("ORDER_AMOUNT");
        if (amountRule != null && amountRule.getThreshold() != null && amountRule.getThreshold() > 0
                && amount != null && amount > amountRule.getThreshold()) {
            return fail("单笔下单金额超上限（" + amountRule.getThreshold() + "）");
        }
        // 2. 市价单滑点上限：|price - best| / best <= slippage
        if (req.getOrderType() != null && req.getOrderType() == 2) {
            RiskRule slipRule = enabledRule("ORDER_SLIPPAGE");
            if (slipRule != null && slipRule.getThreshold() != null && slipRule.getThreshold() > 0) {
                if (req.getSide() != null && req.getSide() == 1) {
                    // 买入用卖一价估算，防止击穿
                    if (req.getBestAsk() != null && req.getBestAsk() > 0 && req.getPrice() != null
                            && req.getPrice() > req.getBestAsk()) {
                        long bps = (req.getPrice() - req.getBestAsk()) * 10000L / req.getBestAsk();
                        if (bps > slipRule.getThreshold()) {
                            return fail("市价单滑点超上限（" + bps + "bps > " + slipRule.getThreshold() + "bps）");
                        }
                    }
                } else if (req.getBestBid() != null && req.getBestBid() > 0 && req.getPrice() != null
                        && req.getPrice() < req.getBestBid()) {
                    long bps = (req.getBestBid() - req.getPrice()) * 10000L / req.getBestBid();
                    if (bps > slipRule.getThreshold()) {
                        return fail("市价单滑点超上限（" + bps + "bps > " + slipRule.getThreshold() + "bps）");
                    }
                }
            }
        }
        return pass();
    }

    @Override
    public AntiPhishing setPhishing(Long userId, String phrase) {
        if (!StringUtils.hasText(phrase) || phrase.length() < 4 || phrase.length() > 64) {
            throw new BusinessException("反钓鱼码长度须为 4-64 字符");
        }
        AntiPhishing exist = phishingMapper.selectOne(new LambdaQueryWrapper<AntiPhishing>()
                .eq(AntiPhishing::getUserId, userId).last("limit 1"), false);
        if (exist != null) {
            exist.setPhrase(phrase);
            phishingMapper.updateById(exist);
            return exist;
        }
        AntiPhishing ap = new AntiPhishing();
        ap.setId(IdWorker.getId());
        ap.setUserId(userId);
        ap.setPhrase(phrase);
        phishingMapper.insert(ap);
        return ap;
    }

    @Override
    public AntiPhishing getPhishing(Long userId) {
        return phishingMapper.selectOne(new LambdaQueryWrapper<AntiPhishing>()
                .eq(AntiPhishing::getUserId, userId).last("limit 1"), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WithdrawRiskResult preCheckWithdraw(WithdrawRiskRequest req) {
        // AML 制裁名单校验：提现收款地址命中制裁/黑名单地址则拦截
        if (req.getToAddress() != null && !req.getToAddress().isBlank()) {
            AmlBlacklist hit = matched("SANCTION_ADDRESS", req.getToAddress().toLowerCase());
            if (hit != null) {
                WithdrawRiskResult r = new WithdrawRiskResult();
                r.setPass(false);
                r.setNeedVerify(false);
                r.setReason("提现地址命中AML制裁/黑名单: " + (hit.getReason() == null ? "sanction" : hit.getReason()));
                return r;
            }
        }
        // 反钓鱼码校验：用户已设置则必须匹配
        AntiPhishing ap = getPhishing(req.getUserId());
        if (ap != null && !ap.getPhrase().equals(req.getPhrase())) {
            WithdrawRiskResult r = new WithdrawRiskResult();
            r.setPass(false);
            r.setReason("反钓鱼码不正确");
            return r;
        }
        // 生成二次验证码
        String code = genVerifyCode();
        WithdrawVerify wv = new WithdrawVerify();
        wv.setId(IdWorker.getId());
        wv.setUserId(req.getUserId());
        wv.setWithdrawId(req.getWithdrawId());
        wv.setVerifyCodeHash(sha256(code));
        wv.setChannel("EMAIL");
        wv.setStatus(0);
        wv.setExpireTime(LocalDateTime.now().plusMinutes(props.getVerifyTtlMinutes()));
        verifyMapper.insert(wv);

        WithdrawRiskResult r = new WithdrawRiskResult();
        r.setPass(true);
        r.setNeedVerify(true);
        r.setVerifyCode(code); // MVP：明文返回便于测试（生产发邮箱/短信）
        r.setReason("已生成二次验证码");
        return r;
    }

    @Override
    public boolean verifyWithdraw(WithdrawVerifyRequest req) {
        WithdrawVerify wv = verifyMapper.selectOne(new LambdaQueryWrapper<WithdrawVerify>()
                .eq(WithdrawVerify::getWithdrawId, req.getWithdrawId())
                .eq(WithdrawVerify::getUserId, req.getUserId())
                .eq(WithdrawVerify::getStatus, 0)
                .orderByDesc(WithdrawVerify::getId)
                .last("limit 1"), false);
        if (wv == null) {
            throw new BusinessException("无待验证的提现记录");
        }
        if (wv.getExpireTime() != null && wv.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("验证码已过期");
        }
        if (!wv.getVerifyCodeHash().equals(sha256(req.getVerifyCode()))) {
            throw new BusinessException("验证码不正确");
        }
        wv.setStatus(1);
        verifyMapper.updateById(wv);
        return true;
    }

    @Override
    public LoginLog recordLogin(LoginRecordRequest req) {
        LoginLog ll = new LoginLog();
        ll.setId(IdWorker.getId());
        ll.setUserId(req.getUserId());
        ll.setUsername(req.getUsername());
        ll.setIp(req.getIp());
        ll.setUserAgent(req.getUserAgent());
        ll.setDevice(detectDevice(req.getUserAgent()));
        ll.setResult(req.getResult() == null ? 0 : req.getResult());
        ll.setRisk(detectRisk(req));
        loginLogMapper.insert(ll);
        return ll;
    }

    @Override
    public Page<LoginLog> pageLoginLogs(Long userId, int page, int size) {
        return loginLogMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<LoginLog>()
                        .eq(LoginLog::getUserId, userId)
                        .orderByDesc(LoginLog::getId));
    }

    // ==================== 内部 ====================

    private OrderRiskResult pass() {
        OrderRiskResult r = new OrderRiskResult();
        r.setPass(true);
        return r;
    }

    private OrderRiskResult fail(String reason) {
        OrderRiskResult r = new OrderRiskResult();
        r.setPass(false);
        r.setReason(reason);
        return r;
    }

    /** AML：按类型+值命中黑名单（生效条目）。 */
    private AmlBlacklist matched(String type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return amlBlacklistMapper.selectOne(new LambdaQueryWrapper<AmlBlacklist>()
                .eq(AmlBlacklist::getMatchType, type)
                .eq(AmlBlacklist::getMatchValue, value)
                .eq(AmlBlacklist::getStatus, 1)
                .last("limit 1"));
    }

    /** AML：查询黑名单清单。 */
    public List<AmlBlacklist> listBlacklist() {
        return amlBlacklistMapper.selectList(new LambdaQueryWrapper<AmlBlacklist>()
                .orderByDesc(AmlBlacklist::getId));
    }

    /** AML：新增黑名单条目。 */
    public AmlBlacklist addBlacklist(String type, String value, String reason) {
        AmlBlacklist b = new AmlBlacklist();
        b.setMatchType(type);
        b.setMatchValue(value);
        b.setReason(reason);
        b.setSource("admin");
        b.setStatus(1);
        b.setCreateTime(LocalDateTime.now());
        amlBlacklistMapper.insert(b);
        return b;
    }

    private RiskRule enabledRule(String type) {
        return ruleMapper.selectOne(new LambdaQueryWrapper<RiskRule>()
                .eq(RiskRule::getRuleType, type)
                .eq(RiskRule::getStatus, 1)
                .orderByAsc(RiskRule::getId)
                .last("limit 1"), false);
    }

    /** 订单名义金额（计价币）：限价=price*qty；市价买=quoteAmount；市价卖=bestBid*qty 估 */
    private Long orderAmount(OrderRiskRequest req) {
        try {
            if (req.getOrderType() != null && req.getOrderType() == 2) {
                if (req.getSide() != null && req.getSide() == 1) {
                    return req.getQuoteAmount();
                }
                return req.getBestBid() != null && req.getQuantity() != null
                        ? req.getBestBid() * req.getQuantity() : null;
            }
            if (req.getPrice() != null && req.getQuantity() != null) {
                return req.getPrice() * req.getQuantity();
            }
            return null;
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    private String genVerifyCode() {
        Random rnd = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < props.getVerifyCodeLength(); i++) {
            sb.append(rnd.nextInt(10));
        }
        return sb.toString();
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException("哈希计算失败");
        }
    }

    private String detectDevice(String ua) {
        if (ua == null) return "unknown";
        String s = ua.toLowerCase();
        if (s.contains("iphone")) return "iPhone";
        if (s.contains("ipad")) return "iPad";
        if (s.contains("android")) return "Android";
        if (s.contains("mac")) return "Mac";
        if (s.contains("windows")) return "Windows";
        return "other";
    }

    /** 简单异常检测：该用户此前从未有同 IP 成功登录则标记异常（异地） */
    private int detectRisk(LoginRecordRequest req) {
        if (req.getResult() != null && req.getResult() == 1) return 0; // 失败不算异地
        if (!StringUtils.hasText(req.getIp())) return 0;
        Long known = loginLogMapper.selectCount(new LambdaQueryWrapper<LoginLog>()
                .eq(LoginLog::getUserId, req.getUserId())
                .eq(LoginLog::getIp, req.getIp())
                .eq(LoginLog::getResult, 0));
        return known > 0 ? 0 : 1;
    }
}
