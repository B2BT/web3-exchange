# Web3-Exchange 项目记忆文档

> 维护者：Hermes Agent · 创建日期：2026-08-04 · 最近更新：2026-08-04（Phase 1 完成）
> 用途：记录项目架构、模块关系、技术栈、业务流程与当前状态，便于后续维护与开发。
> 注意：本文档为只读项目认知基线，修改代码前请先对照此文档确认。
> 配套：`docs/ARCHITECTURE.md`（全局架构设计蓝图，Phase 2 落地的依据）

---

## 一、项目概览

- **项目名**：web3-exchange（Web3 数字资产交易平台，后端微服务）
- **路径**：`/Users/yongzx/IdeaProjects/web3-exchange`
- **构建**：Maven 多模块聚合工程（parent packaging=pom）
- **组织**：`com.web3.exchange` · 版本 `1.0.0`

## 二、架构与模块关系

9 个子模块，统一由父 POM（`dependencyManagement`）管理版本，**Nacos** 做注册中心与配置中心。

| 模块 | 端口 | 职责 | 实现状态 | 编译依赖 |
|------|------|------|----------|----------|
| `exchange-common` | — | 公共类库：统一响应 `Result`、异常体系、Base 实体、监控、MyBatis-Plus 配置 | ✅ 完整 | — |
| `exchange-user` | 8101 | 用户服务：用户 CRUD、鉴权查询 | 🟡 基本可用 | common |
| `exchange-auth` | 8102 | 认证服务：登录/登出/双令牌刷新、验证码 | 🟢 可运行（已实际启动验证） | common |
| `exchange-gateway` | 8080 | API 网关：路由 + JWT 认证过滤 | 🟢 可运行（已实际启动验证，鉴权拦截 401 生效） | common |
| `exchange-asset` | 8103 | 资产服务（规划中） | 🅿️ 空骨架 | — |
| `exchange-order` | 8104 | 订单服务（规划中） | 🅿️ 空骨架 | — |
| `exchange-chain` | 8105 | 链上服务 / web3j（规划中） | 🅿️ 空骨架 | — |
| `exchange-notify` | 8106 | 通知服务（规划中） | 🅿️ 空骨架 | — |
| `exchange-monitor` | 8107 | 监控服务（规划中） | 🅿️ 空骨架 | — |

- **模块间调用**：通过 Feign（`UserServiceClient`），当前 auth → user。
- 空骨架模块仅有 `pom.xml` + `application.yml`，无 Java 代码。

## 三、技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 17 |
| 框架 | Spring Boot | 3.2.5 |
| 微服务 | Spring Cloud | 2023.0.1 |
| 注册/配置中心 | Spring Cloud Alibaba (Nacos) | 2023.0.1.0 / nacos-server 2.4.0 |
| ORM | MyBatis-Plus | 3.5.7 |
| 数据库 | MySQL | 8.0.33（Druid 1.2.20 连接池） |
| 缓存 | Redis 7.2.4（Lettuce 客户端） | — |
| 安全 | Spring Security + JJWT（HS512 双令牌） | 0.11.5 |
| API 文档 | SpringDoc / Swagger UI | 2.3.0 |
| 序列化 | Jackson | — |
| 校验 | spring-boot-starter-validation | — |
| 链上 | **web3j** | 4.10.3（已声明，暂未使用） |
| 工具 | Lombok | 1.18.30 |

## 四、核心业务流程

### 4.1 登录认证流程（auth 服务）
1. 客户端 POST `/api/auth/login`（username/password + captcha）
2. 校验图形验证码（Redis 存储，可开关）
3. Spring Security `AuthenticationManager` 认证（BCrypt 比对，登录失败锁定策略）
4. 生成 **双令牌**：Access Token（2h）+ Refresh Token（30d），HS512 签名，含 userId/username/roles 等 claims
5. Token 写入 Redis：`refresh_token:{jti}`、`user_refresh_tokens:{userId}`、黑名单 `token_blacklist:{hash}`
6. Feign 调 user 服务获取用户详情，组装 `LoginResponse` 返回
7. 记录登录日志（IP、UA）

### 4.2 网关鉴权（gateway，Phase 1 已实现）
- `AuthFilter`（GlobalFilter，WebFlux）：白名单放行 `/api/auth/login|register|captcha`、`/actuator/health`
- 从 `Authorization: Bearer` 提取并校验 JWT（与 auth 同密钥 + JJWT）
- 失败返回 401 统一 JSON（`Result.unauthorized`）；成功透传 `X-User-Id/X-User-Name/X-Authorities` 头

### 4.3 令牌刷新/登出
- `/api/auth/refresh`：用 Refresh 换双令牌（Refresh 可标记"单次使用"）
- `/api/auth/refresh/access`：仅刷 Access
- `/api/auth/logout`：Access 进黑名单 + Refresh 标记已用
- `/api/auth/logout/all`：`revokeAllRefreshTokens` 撤销该用户所有 Refresh

### 4.4 用户服务（user）
- `GET /api/users/list`：用户列表
- `GET /api/users/info/{username}`：鉴权用，返回 `UserDetailDTO`（供 auth Feign 调用）

### 4.5 数据库（表前缀 `t_`，库 `web3_exchange`）
`sql/user.sql` 共 10 张表：`t_user`、`t_role`、`t_permission`、`t_user_role`、`t_role_permission`、`t_dept`、`t_post`、`t_user_dept_post`、`t_login_log`、`t_operation_log`。
`t_user` 关键字段含：账户状态、安全信息（登录失败/锁定）、双因素、用户等级（NORMAL/VIP/SVIP）、邀请码体系、**KYC 认证**（等级 L1-L3、证件、照片）、**钱包信息**（地址、METAMASK/TP 类型）、乐观锁、租户 ID、逻辑删除。

## 五、开发环境（dev-env/docker-compose.yml）
- `mysql:8.0`（3306，root/root，utf8mb4）
- `redis:7`（6379）
- `nacos-server:v2.4.0`（8848/9848，standalone 模式，MySQL 持久化）
- 网络 `dev-net` bridge

## 六、开发进度与已知问题

### Phase 1 已完成（2026-08-04，git 已提交）
- ✅ **auth 服务修复可编译**：补齐 `RefreshTokenRequest`、修 `UserPrincipal` 包路径、清理无效依赖、补 OAuth2 JWT 依赖、修 `application.yml`
- ✅ **gateway 实现 AuthFilter**：JWT 鉴权 + 白名单 + 用户信息透传；从 common 排除 Servlet 栈解决 WebFlux 冲突
- ✅ **修复运行阻塞**：SecurityConfig 重载 @Bean、common application.yml 泄漏服务名、gateway 补主类、补 spring-boot-maven-plugin
- ✅ **auth/gateway 已实际运行验证通过**：auth(8102) 实现 UserDetailsService(Feign 取用户) 后启动成功；gateway(8080) 启动成功且鉴权拦截生效（无 token/伪造 token 均 401）；网关路由转发 auth/login 正常
- ✅ **登录 E2E 已打通**：经网关登录(admin/Test@123)拿到 token(200)、带 token 访问受保护接口放行(200)、无 token 被拦(401)；修复 roles/permissions 为 null 时 NPE、JWT 密钥不足 HS512(≥64字符)
- ✅ **5 个空骨架模块 yml 修正**：name 修正，端口 8103-8107
- ✅ **新增 `docs/ARCHITECTURE.md`**：全局架构设计文档
- ✅ 全项目 `mvn clean package`（temurin-17）通过

### 待办 / 遗留问题
- 🔴 `user/application.yml`：`type-aliases-package` 指向错误包 `com.example.user.entity`（应 `com.web3.exchange.user.entity`）；声明 PageHelper 但依赖未配齐
- 🔴 `UserController.getUserInfo` 返回裸 `UserDetailDTO` 而非统一 `Result<T>`
- 🔴 `UserServiceImpl.userToDetailDTO` 把 `password`/`secretKey` 塞进 DTO（敏感信息泄露风险，已在 /api/users/list 实证）
- ⚠️ 冗余未用类：auth 的 `TokenService`、`SessionService`

### Phase 2 规划（按 docs/ARCHITECTURE.md）
- `exchange-asset`（钱包账户/充值提现/冻结/流水）→ `exchange-chain`（web3j 上链/区块监听/冷热钱包）→ `exchange-order`（撮合+订单）→ 新增 `exchange-market`（行情）→ notify/monitor 填充

## 七、构建/运行提醒
- **必须** `export JAVA_HOME` 指向 temurin-17（`/Users/yongzx/Library/Java/JavaVirtualMachines/temurin-17.0.17/Contents/Home`）；本机 brew openjdk26 与 Lombok 1.18.30 不兼容会编译报错
- 启动顺序：先起 docker-compose（mysql/redis/nacos）→ 再起各微服务
- 服务端口：gateway=8080，user=8101，auth=8102，asset=8103，order=8104，chain=8105，notify=8106，monitor=8107
