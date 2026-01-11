### 结构说明
```
exchange-auth/
├── src/main/java/com/web3/exchange/auth/
│   ├── config/                    # 配置类
│   │   ├── SecurityConfig.java           # 安全配置
│   │   ├── PasswordConfig.java           # 密码配置
│   │   ├── RedisConfig.java             # Redis配置
│   │   ├── FeignConfig.java             # Feign配置
│   │   └── JwtConfig.java               # JWT配置
│   │
│   ├── controller/               # 控制器
│   │   ├── AuthController.java          # 认证接口
│   │   ├── TokenController.java         # Token管理
│   │   └── CaptchaController.java       # 验证码
│   │
│   ├── service/                  # 服务层
│   │   ├── impl/
│   │   │   ├── AuthServiceImpl.java    # 认证服务
│   │   │   ├── UserDetailsServiceImpl.java  # UserDetails
│   │   │   └── TokenServiceImpl.java   # Token服务
│   │   ├── AuthService.java
│   │   └── TokenService.java
│   │
│   ├── security/                # 安全相关
│   │   ├── JwtAuthenticationFilter.java    # JWT过滤器
│   │   ├── JwtTokenProvider.java          # JWT工具
│   │   └── exception/                     # 异常处理
│   │
│   ├── feign/                   # Feign客户端
│   │   ├── UserServiceClient.java
│   │   └── fallback/
│   │
│   └── domain/                  # 领域对象
│       └── UserPrincipal.java           # 用户主体
│
└── resources/
├── application.yml                  # 主配置
└── bootstrap.yml                    # 启动配置
```