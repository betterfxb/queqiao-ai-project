@echo off
set "JAVA_HOME=C:\Users\19560\.jdks\corretto-17.0.10"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d D:\ai_project\queqiao-server-java
call D:\ai_project\.tools\apache-maven-3.9.9\bin\mvn.cmd %*
