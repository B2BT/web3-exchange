#!/bin/bash
# 批量启动 web3-exchange 全部业务服务
export JAVA_HOME=/Users/yongzx/Library/Java/JavaVirtualMachines/temurin-17.0.17/Contents/Home
export LOG_PATH=/Users/yongzx/logs
cd /Users/yongzx/IdeaProjects/web3-exchange

SERVICES="auth:8102 user:8101 order:8104 asset:8103 market:8106 chain:8105 notify:8107 monitor:8108 admin:8109 margin:8110 staking:8112 risk:8114 ticket:8116 futures:8117 gateway:8080"

for entry in $SERVICES; do
  mod="${entry%%:*}"
  port="${entry##*:}"
  if lsof -iTCP:$port -sTCP:LISTEN -P 2>/dev/null | grep -q LISTEN; then
    pkill -9 -f "exchange-$mod-1.0.0.jar" 2>/dev/null
    sleep 1
  fi
  jar="exchange-$mod/target/exchange-$mod-1.0.0.jar"
  if [ -f "$jar" ]; then
    nohup $JAVA_HOME/bin/java -jar "$jar" > /tmp/$mod.log 2>&1 &
    echo "启动 $mod ($port) pid=$!"
  else
    echo "⚠️ 无jar: $jar"
  fi
done
echo "全部启动命令已发出"