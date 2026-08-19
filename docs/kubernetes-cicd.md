# Kubernetes 部署 + CI/CD 流水线

> 在 macOS 本地用 kind 搭建 Kubernetes，集成 CI（GitHub Actions 构建镜像）+ CD（ArgoCD GitOps 自动部署）。

## 架构

```
GitHub 仓库 (web3-exchange)
   │
   ├── push main → GitHub Actions (CI)
   │      ├── Maven 构建 → 15 个 jar
   │      └── Docker 构建 → 推 ghcr.io/b2bt/web3-exchange-<svc>
   │
   └── k8s/ 清单变更 → ArgoCD (CD) 检测
          └── 自动同步部署到 kind 集群
                └── exchange namespace 各服务 Deployment/Service
```

## 组件

| 组件 | 说明 |
|------|------|
| **kind** | 本地 K8s 集群（`kind-web3-dev`，单节点，基于 Docker） |
| **ArgoCD** | GitOps 工具，监听 Git 仓库清单，自动同步部署 |
| **GitHub Actions** | CI：构建 Maven + Docker 镜像，推 GHCR |
| **GHCR** | GitHub Container Registry，存 15 个服务镜像 |

## 快速开始

### 1. 创建本地集群
```bash
brew install kind
kind create cluster --config k8s/kind-config.yaml
# 端口：网关 NodePort 30080 → 宿主 38080；ArgoCD UI 38081
```

### 2. 安装 ArgoCD
```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
# 初始密码
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
```

### 3. 本地构建 + 部署单个服务（验证）
```bash
# 构建镜像（jar 在 target/）
docker build --build-arg SERVICE=order -f k8s/Dockerfile -t ghcr.io/b2bt/web3-exchange-order:latest .
# 载入 kind
kind load docker-image ghcr.io/b2bt/web3-exchange-order:latest --name web3-dev
# 应用清单
kubectl apply -k k8s/base
```

### 4. ArgoCD 应用（GitOps 自动同步）
```bash
# 方式 A：HTTPS + PAT（推荐，需 GitHub PAT：repo 权限）
argocd login localhost:38081 --username admin --password <初始密码> --insecure --grpc-web
argocd repo add https://github.com/B2BT/web3-exchange.git \
  --username <github用户名> --password <PAT> --insecure-skip-server-verification
kubectl apply -f k8s/argocd-app.yaml   # 应用时把 repoURL 改为 HTTPS 地址
# ArgoCD 检测 main 分支 k8s/ 清单变化 → 自动同步部署

# 方式 B：SSH key（本机 SSH 通但 ArgoCD 容器内 handshake 失败时，检查 key 为 OpenSSH 格式）
argocd repo add git@github.com:B2BT/web3-exchange.git \
  --ssh-private-key-path ~/.ssh/id_ed25519 --insecure-ignore-host-key
```

### 5. 全量部署验证（本地 kind，已实测）
```bash
# 构建全部镜像 + 载入 kind（15 服务）
./k8s/build-all.sh
# 部署基础设施 + 全量服务
kubectl apply -k k8s/infra
kubectl apply -k k8s/base
# 经网关访问（NodePort 38080）
curl localhost:38080/api/market/ticker/list   # → 200 真实行情
```

## 目录结构

```
.github/workflows/ci.yml   # CI 流水线（构建 jar → Docker 镜像 → 推 GHCR）
k8s/
├── kind-config.yaml       # kind 集群配置（端口映射）
├── Dockerfile             # 通用 Spring Boot 服务镜像（build-arg SERVICE）
├── base/                  # Kustomize 部署清单（namespace/deployment/service）
│   ├── namespace.yaml
│   ├── kustomization.yaml
│   ├── order.yaml         # 示例服务（deployment + service）
│   └── gateway.yaml       # 网关（NodePort 30080）
└── argocd-app.yaml        # ArgoCD Application（GitOps）
```

## 已验证

- ✅ kind 集群创建、节点 Ready
- ✅ ArgoCD 7 组件全部 Running（CLI 登录成功）
- ✅ CI 流水线：GitHub Actions **16 job 全成功**，**GHCR 15 个镜像** 已推送
- ✅ 基础设施 k8s 化：MySQL(StatefulSet+PVC)/Redis/Nacos/Kafka(KRaft)/RocketMQ 全部 Running
  - SQL schema 从本机 dev-mysql 导出导入（nacos_config + web3_exchange 46 表）
  - Kafka topic 创建成功、RocketMQ broker 注册正常
- ✅ **全量 15 个服务部署到 k8s，全部 Ready**
  - 经网关 38080：`/api/market/ticker/list` → 200 + 7 个真实 ticker（BTC $64k+ 等）
  - `/api/market/kline/list` → 200 真实 K 线；auth/chain/asset 鉴权 401（正常）
- ✅ **ArgoCD GitOps 闭环打通**（HTTPS+PAT 拉私有 repo）
  - `push main → ArgoCD 自动同步 → 集群全部 Synced+Healthy`
  - 服务端到端可用

## 排坑记录（k8s 部署实战）

1. **日志目录 /logs 不可写**：Logback 写 /logs/<svc>/app.log，pod 内目录不存在 → Spring 上下文异常 → Controller 不注册 → 接口全"404/500"。修复：Deployment 挂 emptyDir 到 /logs。
2. **readiness 探针**：部分服务无 actuator，HTTP 探针 404 永不就绪 → 改用 TCP 探针。
3. **market 启动失败**：DefiPriceSource `@Value("#{...:}}")` 空 SpEL 默认值解析异常 → 改 `: {}}`。
4. **monitor 启动失败**：application.yml 重复 management 段（YAML DuplicateKey）→ 合并。
5. **Nacos 注册**：gateway 的 nacos 在 `discovery` 下配置，需 `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` 单独覆盖。
6. **Kafka readiness**：探针用 service 名连不上（endpoints 空死循环）→ 用 localhost 直连。
7. **RocketMQ OOM**：默认 JVM 堆超容器 limit → JAVA_OPT_EXT 限堆 + 提高 limit。

## 注意点

1. **镜像**：本地 kind 用 `kind load` + `imagePullPolicy: IfNotPresent`；CI 推 GHCR 后可改 Always。
2. **生产 tag**：latest 触发生成问题，生产建议用不可变 tag（sha）。
3. **私有 repo**：ArgoCD 拉取需 HTTPS+PAT（推荐）或 SSH key（容器内 handshake 可能失败）。
4. **CI 验证**：需 GitHub PAT 查看 Actions 运行；并发环境可用 build-all.sh 本地验证镜像构建。

## 完整生产部署（后续）

- **ArgoCD 全自动**：配好 repo 凭据后，push main → CI 推镜像 → ArgoCD 自动同步清单（GitOps 闭环）
- **ELK**：可 k8s 化（StatefulSet/PVC），Filebeat 采集 /logs（emptyDir 已挂载）
- **多副本**：内存态服务（futures 撮合簿、market 聚合器）需处理多副本一致性
