# KYC-AML 合规

> 反洗钱合规三要素：**KYC（实名认证）已有 + AML（黑名单/制裁名单）本次补齐**。
> 制裁/欺诈名单命中的提现地址在风控前置阶段即被拦截，阻止资金流向高风险地址。

## 一、KYC 实名认证（已有）

- `KYCSubmitDTO`（realName/idCardType/idCardNo/证件照片）+ `UserServiceImpl`
- 提交流程：提交→置「审核中」(kyc_status=1)，已审核/审核中不可重复提交
- `t_user` 已含 kyc_status/kyc_level/id_card_*/kyc_verify_time 字段

## 二、AML 黑名单/制裁名单（本次新增）

### 表：`t_aml_blacklist`
| 字段 | 说明 |
|------|------|
| match_type | `PERSON_NAME` / `PERSON_ID_CARD` / `SANCTION_ADDRESS` |
| match_value | 姓名 / 证件号 / 制裁钱包地址 |
| reason/source/status | 原因 / 来源 / 是否生效 |
| create/update_time | 时间戳 |

### 拦截点：`RiskServiceImpl.preCheckWithdraw`
- 提现时校验收款地址命中 `SANCTION_ADDRESS` 黑名单 → **拦截**（不生成二次验证码）
- 未命中 → 走原有反钓鱼码 + 二次验证流程

### 接口
- `GET /internal/risk/aml/blacklist`：黑名单清单
- `addBlacklist(type,value,reason)`：新增名单（admin 后台可调用）

### 验证（实测）
```
提现到制裁地址 0x7099... → pass=False, "提现地址命中AML制裁/黑名单" ✅ 拦截
提现到普通地址 0x1111... → pass=True, needVerify=True ✅ 放行
GET /internal/risk/aml/blacklist → 1 条
```

## 三、生产进一步（可选）

1. **接真实制裁名单**（OFAC SDN / EU 制裁）定时同步
2. **KYC 打回流程** + 审核后台（admin）
3. **交易监控**：大额/频繁拆分交易异常识别（结构化交易 Structuring 检测）
4. **风险评分**：用户行为评分，高风险冻结

## 相关
- `docs/production-gap.md`
- KYC: `exchange-user` / AML: `exchange-risk`