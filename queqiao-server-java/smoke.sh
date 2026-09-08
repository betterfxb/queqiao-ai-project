#!/usr/bin/env bash
# 后端接口冒烟测试：登录取 token 后逐个打接口，输出 HTTP 状态 + 响应摘要
BASE="http://127.0.0.1:8080/api"

TOKEN=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' | python -c "import sys,json;print(json.load(sys.stdin).get('token',''))" 2>/dev/null)

if [ -z "$TOKEN" ]; then echo "!! 登录失败，终止"; exit 1; fi
echo "== 登录成功 token=${TOKEN:0:24}..."
echo

hit() {
  local method="$1"; local path="$2"; local body="$3"
  local out
  if [ -n "$body" ]; then
    out=$(curl -s -w "\n@@%{http_code}" -X "$method" "$BASE$path" \
      -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$body")
  else
    out=$(curl -s -w "\n@@%{http_code}" -X "$method" "$BASE$path" -H "Authorization: Bearer $TOKEN")
  fi
  local code=$(echo "$out" | tail -1 | sed 's/@@//')
  local data=$(echo "$out" | sed '$d' | head -c 180 | tr -d '\n')
  if [ "$code" = "200" ]; then printf "  OK  %-3s %-38s %s\n" "$code" "$method $path" "$data"
  else printf "  ERR %-3s %-38s %s\n" "$code" "$method $path" "$data"; fi
}

echo "-- 认证 / 系统 --"
hit GET  /auth/me
hit GET  /health
hit GET  /settings
hit GET  /logs

echo "-- 工作台 --"
hit GET  /dashboard
hit GET  /dashboard/kpis
hit GET  /dashboard/kanban
hit GET  /dashboard/todos
hit GET  /dashboard/recs

echo "-- 会员 --"
hit GET  "/members?page=1&size=2"
hit GET  /members/stats

echo "-- 红娘 --"
hit GET  /agents
hit GET  /agents/ranking
hit GET  /agents/stats

echo "-- 牵线 --"
hit GET  "/pairs?page=1&size=2"
hit GET  /pairs/stats
hit GET  "/match/recommendations?memberId=M2026088&limit=3"

echo "-- 审核 --"
hit GET  /audits
hit GET  /audits/stats

echo "-- 活动 --"
hit GET  /activities
hit GET  /activities/stats

echo "-- 财务 --"
hit GET  /finance/records
hit GET  /finance/stats

echo "-- 报表 --"
hit GET  /reports/records
hit GET  /reports/summary

echo "-- CRM --"
hit GET  /crm/board
hit GET  /crm/stats

echo "-- 消息 --"
hit GET  /conversations
hit GET  /conversations/unread

echo "-- 日历 --"
hit GET  /calendar/events
hit GET  /calendar/today
hit GET  /calendar/stats

echo "-- 大屏 --"
hit GET  /screen
