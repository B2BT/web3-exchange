# 公共基座 exchange-common

> 所有微服务的公共依赖基座，提供统一响应模型、异常体系、全局异常处理、基础实体、
> 跨服务 DTO 契约、配置、监控埋点等。各服务通过依赖 `exchange-common` 共享这些通用能力。
> **这是读懂本项目"一套代码风格"的入口**——先看本文，再读各域文档。

## 定位

`exchange-common` 本身**不启动**（非 Spring Boot 应用），而是被所有 `exchange-*` 服务依赖的公共库。
它统一了：
1. **API 响应契约**（Result / PageData）——所有接口返回统一结构，前端/网关可统一解析
2. **异常体系**（分级异常 + 全局处理器）——错误码 + 统一错误响应
3. **跨服务 DTO**（asset 资金操作 / user 用户信息 / order 成交）——服务间 Feign 契约
4. **基础实体/配置**（BaseEntity / MyBatis-Plus 配置）

## 一、统一响应模型

### `model.Result<T>`（最核心）
所有 controller 返回统一包一层 `Result<T>`：
```java
{ "code": 200, "message": "success", "data": {...}, "timestamp": ..., "requestId": ..., "success": true, "error": false }
```
- `code=200` 成功；`code:4xx/5xx` 失败（错误码见 `ErrorCode`）
- `Result.success(data)` / `Result.success(data, msg)` 构造成功；`Result.error(...)` 构造失败
- 前端/网关凭 `code` 判断成败，不直接依赖 HTTP status

### `model.PageData<T>`
统一分页返回模型：`{total, current, size, pages, records}`。
所有分页接口（订单列表、流水、成交历史等）统一用它，保证前端分页组件通用。

## 二、异常体系

### 分级异常（`exception/*`）
| 异常 | 语义 |
|------|------|
| `BaseException` | 异常基类 |
| `BusinessException` | 业务规则失败（如余额不足、重复下单） |
| `NotFoundException` | 资源不存在 |
| `ValidationException` | 参数校验失败 |
| `PermissionException` | 无权限 |
| `AuthException` | 认证失败（401） |
| `ServiceException` | 服务层通用异常 |
| `ContractException` / `GasException` / `SignatureException` / `TokenException` / `TransactionException` / `WalletException` | 链上/钱包领域异常（web3 子包） |

### `handler.GlobalExceptionHandler`
`@RestControllerAdvice` 全局异常处理器：统一捕获上述异常 → 转成 `Result<T>` 错误响应。
**关键作用**：服务内无论哪里抛业务异常，最终都返回统一 JSON，**不会裸返回 Spring 默认错误页**。
各服务通过 `@Import(GlobalExceptionHandler.class)` 引入（见各服务启动类）。

### `constant.ErrorCode`
集中错误码常量，保证全局错误码唯一、不冲突。

## 三、跨服务 DTO 契约（`asset.dto` / `user` / `order.dto`）

服务间用 Feign 调用，DTO 定义在公共层保证两端一致：
- **`asset.dto`**：`FreezeRequest`(冻结) / `UnfreezeRequest`(解冻) / `TransferRequest`(过户) / `CreditRequest`(入账) / `AccountVO` / `LedgerVO` —— order/chain 调 asset 资金操作时用
- **`user`**：`UserDTO` / `UserDetailDTO` / `UserQueryDTO` —— auth 调 user 取用户信息
- **`order.dto.TradeSettleDTO`**：成交结算契约

> **约定**：跨服务 DTO 放 common 而非各服务私有，保证 Feign 序列化字段一致、版本可控。

## 四、基础实体 / 配置

### `entity.base`
- `BaseEntity`：审计字段基类（create_time / update_time / version 乐观锁 / is_deleted 逻辑删除）
- `BaseDTO` / `BaseVO`：数据传输/视图对象基类
- **核心约定**：所有业务实体继承 BaseEntity，自动获得 `@Version` 乐观锁 + 逻辑删除，实现并发安全

### `config.MyBatisPlusConfig`
- 分页插件（PaginationInnerInterceptor）+ 乐观锁（OptimisticLockerInnerInterceptor）+ 逻辑删除
- **作用**：全项目分页（PageData）与乐观锁（@Version）自动生效的来源

## 五、其他

- `monitor.ExceptionMonitor`：异常埋点（辅助监控，配合 Prometheus/metrics）
- `util.ExceptionUtil`：异常工具
- `order.dto`：成交消息契约

## 六、公共依赖（pom 提供的能力）

依赖 `exchange-common` 的服务自动获得：
- Spring Web / Actuator（健康检查 + Prometheus 指标暴露）
- `micrometer-registry-prometheus`（`/actuator/prometheus`，监控告警用）
- `logstash-logback-encoder`（结构化 JSON 日志 → ELK）
- MyBatis-Plus（分页/乐观锁/逻辑删除）
- Redis（StringRedisTemplate 分布式缓存/幂等）
- 配置的版本由父 pom / Spring Boot BOM 统一管理

## 七、读懂路径

```
common-domain.md(本文) → 各域 domain 文档(asset/order/chain/market/futures...)
                                       → ARCHITECTURE.md(整体拓扑)
                                       → feature-guide.md(功能速览)
```

## 相关文档
- `docs/README.md` 项目文档索引
- `docs/ARCHITECTURE.md` 架构蓝图
- 各域文档 `docs/*-domain.md`