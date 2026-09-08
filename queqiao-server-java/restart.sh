#!/usr/bin/env bash
# 重启后端：停旧进程 → 构建 → 后台启动 → 等待健康检查通过
# 用法： bash restart.sh            默认 mysql profile
#        PROFILE=prod bash restart.sh  生产 profile
set -e
cd "$(dirname "$0")"
export JAVA_HOME="C:\\Users\\19560\\.jdks\\corretto-17.0.10"
export SERVER_PORT=8080
MVN="D:/ai_project/.tools/apache-maven-3.9.9/bin/mvn.cmd"
PROFILE="${PROFILE:-mysql}"
LOG=/tmp/qq-backend.log

echo "[1/4] 停止旧进程"
PID=$(netstat -ano 2>/dev/null | grep ":${SERVER_PORT}" | grep LISTENING | head -1 | awk '{print $5}')
if [ -n "$PID" ]; then taskkill /F /PID "$PID" 2>/dev/null; sleep 2; fi

echo "[2/4] 构建 fat jar"
"$MVN" -B -q package -DskipTests -Denforcer.skip=true

echo "[3/4] 启动 :${SERVER_PORT} (后台, profile=$PROFILE)"
nohup "$JAVA_HOME/bin/java.exe" -jar target/queqiao-server-1.0.0.jar --spring.profiles.active="$PROFILE" > "$LOG" 2>&1 &
echo "  pid=$!"

echo "[4/4] 等待健康检查"
i=0
while [ $i -lt 60 ]; do
  code=$(curl -s -o /dev/null -w "%{http_code}" -H "User-Agent: boot" "http://127.0.0.1:${SERVER_PORT}/api/health" 2>/dev/null || echo 000)
  if [ "$code" = "200" ]; then echo "  后端就绪（第 ${i} 次探测）"; exit 0; fi
  sleep 1; i=$((i+1))
done
echo "  !! 超时，最近日志："; tail -20 "$LOG"; exit 1
