#!/usr/bin/env bash
# 轮询等待服务就绪。用法：bash waitup.sh <url> [最大秒数]
URL="${1:-http://127.0.0.1:8080/api/health}"
MAX="${2:-90}"
i=0
while [ "$i" -lt "$MAX" ]; do
  code=$(curl -s -o /dev/null -w "%{http_code}" -H "User-Agent: boot" "$URL" 2>/dev/null || echo 000)
  if [ "$code" = "200" ]; then echo "READY after ${i}s -> $URL"; exit 0; fi
  python -c "import time;time.sleep(1)"
  i=$((i+1))
done
echo "TIMEOUT after ${MAX}s -> $URL (last code=$code)"
exit 1
