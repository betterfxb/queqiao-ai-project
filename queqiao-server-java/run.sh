#!/usr/bin/env bash
# 构建并启动后端（默认 mysql profile；生产用 PROFILE=prod 切换）
# 用法： bash run.sh               前台运行（mysql profile）
#        PROFILE=prod bash run.sh  前台运行（生产 profile，需先 export 密钥环境变量）
set -e
cd "$(dirname "$0")"
export JAVA_HOME="C:\\Users\\19560\\.jdks\\corretto-17.0.10"
export SERVER_PORT=8080
MVN="D:/ai_project/.tools/apache-maven-3.9.9/bin/mvn.cmd"
PROFILE="${PROFILE:-mysql}"

echo "[1/2] 构建 fat jar (Maven 3.9.9)"
"$MVN" -B -q package -DskipTests -Denforcer.skip=true

echo "[2/2] 启动 :${SERVER_PORT} (profile=$PROFILE)"
exec "$JAVA_HOME/bin/java.exe" -jar target/queqiao-server-1.0.0.jar --spring.profiles.active="$PROFILE" --server.port="${SERVER_PORT}"
