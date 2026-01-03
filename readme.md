```bash
auth-service/
├── src/main/java/com/example/auth/
│   ├── AuthApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── RedisConfig.java
│   │   └── ResourceServerConfig.java
│   ├── controller/
│   │   └── AuthController.java
│   ├── service/
│   │   ├── AuthService.java
│   │   ├── impl/AuthServiceImpl.java
│   │   └── TokenService.java
│   ├── security/
│   │   ├── jwt/
│   │   │   ├── JwtTokenProvider.java
│   │   │   └── JwtAuthenticationConverter.java
│   │   └── domain/
│   │       └── UserPrincipal.java
│   ├── feign/
│   │   └── UserServiceClient.java
│   ├── dto/
│   │   ├── request/
│   │   │   ├── LoginRequest.java
│   │   │   └── TokenRequest.java
│   │   └── response/
│   │       ├── AuthResponse.java
│   │       └── TokenResponse.java
│   └── utils/
│       └── RedisUtil.java
│
└── src/main/resources/
└── application.yml

#### 总体顺序
SecurityConfig
   ↓
UserDetailsService        ← 用户从哪来
   ↓
PasswordEncoder          ← 密码怎么比
   ↓
登录接口（/login）        ← JWT 在这生成
   ↓
JWT 工具类
   ↓
JWT 过滤器                ← 每次请求校验
   ↓
权限控制（@PreAuthorize）
```