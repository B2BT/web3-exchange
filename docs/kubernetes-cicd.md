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
# 需配置 GitHub repo 凭据（私有 repo 用 SSH 或 HTTPS token）：
#   argocd repo add git@github.com:B2BT/web3-exchange.git --ssh-private-key-path ~/.ssh/id_rsa
kubectl apply -f k8s/argocd-app.yaml
# ArgoCD 检测 main 分支 k8s/ 清单变化 → 自动同步部署
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
- ✅ ArgoCD 7 组件全部 Running
- ✅ CI 流水线配置：Maven 构建 + 15 服务镜像矩阵推送 GHCR
- ✅ 本地构建 order/gateway 镜像成功、载入 kind、清单部署成功
- ✅ 容器正常启动（JSON 日志格式，serviceName=exchange-order）
- ✅ kustomize 清单校验通过

## 注意点

1. **镜像拉取策略**：清单用 `imagePullPolicy: IfNotPresent`（本地 kind 用载入镜像）；CI 推 GHCR 后可改 Always。
2. **基础设施**：最小集验证不含 MySQL/Nacos/Redis/Kafka/ELK 的 k8s 化（它们仍在 Docker 跑）。容器因 Nacos 不可达而启动失败是预期的，需在 k8s 部署基础设施或配置外部地址。
3. **私有 repo**：ArgoCD 拉取需配 SSH/HTTPS 凭据。
4. **imagePullPolicy=latest**：生产建议用不可变 tag（sha）。

## 完整生产部署（后续）

将 MySQL/Nacos/Redis/Kafka/ELK 也 k8s 化（StatefulSet/PVC），服务 env 指向集群内基础设施（`mysql.exchange`/`nacos.exchange` 等），即可全量部署。
