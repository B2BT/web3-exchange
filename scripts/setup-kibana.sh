#!/bin/bash
# 一键配置 Kibana：等待就绪 + 创建 web3-logs data view
# 用法: bash scripts/setup-kibana.sh
KIBANA="http://localhost:5601"
DATA_VIEW_TITLE="web3-logs-*"
DATA_VIEW_NAME="web3-logs"

echo "等待 Kibana 就绪..."
for i in $(seq 1 30); do
  code=$(curl -s -o /dev/null -w "%{http_code}" "$KIBANA/api/status" 2>/dev/null)
  if [ "$code" = "200" ]; then
    echo "Kibana 就绪 (HTTP $code)"
    break
  fi
  sleep 5
done

if [ "$code" != "200" ]; then
  echo "❌ Kibana 未就绪 (HTTP $code)。请确认 Kibana 容器已启动且 ES 可达。"
  exit 1
fi

echo "创建 data view: $DATA_VIEW_TITLE ..."
RESP=$(curl -s -X POST "$KIBANA/api/data_views/data_view" \
  -H "Content-Type: application/json" -H "kbn-xsrf: true" \
  -d "{\"data_view\":{\"title\":\"$DATA_VIEW_TITLE\",\"name\":\"$DATA_VIEW_NAME\",\"timeFieldName\":\"@timestamp\"}}")

if echo "$RESP" | grep -q '"id"'; then
  DV_ID=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['data_view']['id'])" 2>/dev/null)
  echo "✅ data view 创建成功: id=$DV_ID"
  echo "打开 http://localhost:5601 → 左侧菜单 'Discover' 即可检索所有微服务日志"
else
  echo "⚠️ data view 创建响应: $RESP"
  echo "（若提示已存在，可忽略；在 Kibana → Management → Data Views 确认 web3-logs-* 已存在）"
fi
