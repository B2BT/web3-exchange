#!/bin/bash
# 批量构建全部服务镜像并载入 kind（模拟 CI 流水线产物）
set -e
cd /Users/yongzx/IdeaProjects/web3-exchange
SERVICES="auth user asset market chain notify monitor admin margin staking risk ticket futures order gateway"
for s in $SERVICES; do
  echo "=== 构建 $s ==="
  docker build --build-arg SERVICE=$s -f k8s/Dockerfile -t ghcr.io/b2bt/web3-exchange-$s:latest . > /tmp/build-$s.log 2>&1 || { echo "FAIL $s"; tail -3 /tmp/build-$s.log; continue; }
  echo "=== 载入 $s ==="
  kind load docker-image ghcr.io/b2bt/web3-exchange-$s:latest --name web3-dev > /tmp/load-$s.log 2>&1 || echo "load warn $s"
done
echo "=== 全部完成 ==="