#!/bin/bash
# 本地受控运行时封装：固定 JDK17 + 指定 Maven
export JAVA_HOME="C:\\Users\\19560\\.jdks\\corretto-17.0.10"
export PATH="/c/Users/19560/.jdks/corretto-17.0.10/bin:$PATH"
export MAVEN_OPTS="-Dfile.encoding=UTF-8"
cd /d/ai_project/queqiao-server-java || exit 1
exec /d/ai_project/.tools/apache-maven-3.9.9/bin/mvn "$@"
