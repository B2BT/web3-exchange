```
exchange-common/                    # 公共模块
├── src/main/java/com/web3/exchange/common/
│   ├── exception/                  # 异常类
│   │   ├── BaseException.java                     # 异常基类
│   │   ├── BusinessException.java                 # 业务异常
│   │   ├── AuthException.java                     # 认证异常
│   │   ├── PermissionException.java               # 权限异常
│   │   ├── NotFoundException.java                 # 资源未找到
│   │   ├── ServiceException.java                  # 服务异常
│   │   ├── ValidationException.java               # 参数验证异常
│   │   ├── web3/                                  # Web3相关异常
│   │   │   ├── WalletException.java              # 钱包异常
│   │   │   ├── TransactionException.java         # 交易异常
│   │   │   ├── ContractException.java            # 合约异常
│   │   │   ├── SignatureException.java           # 签名异常
│   │   │   ├── NetworkException.java             # 网络异常
│   │   │   ├── GasException.java                 # Gas异常
│   │   │   ├── TokenException.java               # 代币异常
│   │   │   ├── ChainException.java               # 链异常
│   │   │   └── SmartContractException.java       # 智能合约异常
│   │   └── feign/                                # Feign相关异常
│   │       ├── FeignClientException.java         # Feign客户端异常
│   │       └── CircuitBreakerException.java      # 熔断器异常
│   │
│   ├── result/                     # 统一响应
│   │   ├── Result.java                           # 统一响应类
│   │   ├── PageResult.java                       # 分页响应
│   │   └── ApiResult.java                        # API响应
│   │
│   ├── constants/                  # 常量
│   │   ├── ErrorCode.java                        # 错误码常量
│   │   ├── Web3Constants.java                    # Web3常量
│   │   └── BusinessConstants.java                # 业务常量
│   │
│   ├── dto/                        # DTO
│   ├── entity/                     # 实体
│   ├── enums/                      # 枚举
│   └── utils/                      # 工具类
│       ├── ExceptionUtil.java                     # 异常工具类
│       └── ResultUtil.java                        # 响应工具类
│
└── src/main/resources/
    ├── messages/                   # 国际化消息
    │   ├── messages.properties                   # 默认消息
    │   ├── messages_zh_CN.properties             # 中文消息
    │   └── messages_en_US.properties             # 英文消息
    └── error-codes.properties      # 错误码配置
```