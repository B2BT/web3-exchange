# 密钥外部化与托管（KMS/HSM/Vault 思路）

> 资金安全第一梯队第 2 项。将密钥从 yml 明文迁移为"环境变量/Secret/KMS 注入"，并加生产防呆，防止 mock 私钥带到生产。
> 生产完整方案是 KMS/HSM/Vault（云厂商托管密钥，解密在硬件/服务内），本文给出落地切口与接入说明。

## 现状改造（已完成）

### 1. 密钥全部外部化（yml → ${ENV:default}）

| 密钥 | 原(yaml 明文) | 新 | 环境变量 |
|------|--------------|-----|---------|
| 热钱包私钥 | chain.hot-wallet.private-key | `${CHAIN_HOT_PRIVATE_KEY:...}` | `CHAIN_HOT_PRIVATE_KEY` |
| HD 主助记词 | chain.hd-wallet.mnemonic | `${CHAIN_HD_MNEMONIC:...}` | `CHAIN_HD_MNEMONIC` |
| 自托管加密密钥 | chain.self-wallet.encrypt-secret | `${CHAIN_SELF_ENCRYPT_SECRET:...}` | `CHAIN_SELF_ENCRYPT_SECRET` |
| JWT 密钥(auth) | jwt.secret | `${JWT_SECRET:...}` | `JWT_SECRET` |
| JWT 密钥(gateway) | jwt.secret(硬编码) | `${JWT_SECRET:...}` | `JWT_SECRET` |

默认值仅本地开发兜底；生产通过环境变量 / K8s Secret / Vault 注入真实值。

### 2. 生产防呆（生效验证）

- **ChainProperties** 实现 `InitializingBean.afterPropertiesSet()`：prod profile + 默认/开发密钥 → 抛异常拒启
- **JwtInitializer**（修 bug）：注入真实 JwtConfig 而非 `new JwtConfig()`，prod + 默认 JWT → 拒启

验证结果：
```
prod + 默认密钥            → 拒绝启动(生产环境禁用开发/默认钱包密钥) ✅
prod + 真实密钥(合法BIP39) → 正常启动 Started ChainApplication ✅
```

## 生产完整方案（KMS/HSM/Vault）

### 分层
```
┌─ KMS/HSM/Vault（云托管, 密钥不落应用内存）
│     decrypt 得到明文密钥(仅在内存短暂持有)
├─ 部署时由 CI/CD/运维注入环境变量(get-secret → env)
└─ 应用: ${ENV:default} 读取, 生产无默认值即启动失败
```

### 推荐
| 云 | 服务 |
|----|------|
| AWS | AWS KMS + Secrets Manager / aws-ssm |
| 阿里云 | KMS + 凭据管家 |
| 开源 | HashiCorp Vault / Kubernetes External Secrets + sealed-secrets |

### 注意事项
1. **私钥绝不落日志/DB/镜像**（本项目已遵守 [REDACTED] 纪律）
2. **助记词/私钥备份**：KMS 不应直接存助记词（助记词需要离线冷备，2-of-3 分片存储）
3. **密钥轮换**：定期轮换 JWT/加密密钥，DB 存量需 AES-GCM 带版本头支持平滑迁移
4. 本实现在 dev/mock 单机演示；生产需结合云 KMS 落地

## 相关文档
- `docs/production-gap.md` — 生产差距总路线图
- `docs/cold-wallet.md` — 冷热钱包分离（前一项）
