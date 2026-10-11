@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

rem ============================================================
rem  鹊桥·红娘工作台 —— 一键启动（AI 8090 / 后端 8080 / 前端 5173）
rem  用途：机器重启或进程被杀后，双击本文件即可恢复整套联调环境。
rem  特性：已在运行的服务自动跳过，不重复拉起；逐个等待就绪并自检。
rem ============================================================

rem ---------- 依赖路径（换机器时只需改这一段）----------
set "JDK=D:\ai_project\.tooling\jdk-17.0.20.1+1\bin\java.exe"
set "JARDIR=D:\ai_project\deploy\release\backend-java"
set "JAR=queqiao-server-1.0.0.jar"
set "AIDIR=D:\ai_project\queqiao-ai"
set "PY=%AIDIR%\.venv\Scripts\python.exe"
set "FEDIR=D:\ai_project\queqiao-workbench"
set "NODEBIN=C:\Users\19560\.workbuddy\binaries\node\versions\22.22.2-2"
set "NPM=%NODEBIN%\npm.cmd"

echo ============================================================
echo   鹊桥·红娘工作台   一键启动
echo ============================================================
echo.

rem ---------- 依赖自检 ----------
if not exist "%JDK%"  ( echo [错误] 找不到 JDK17：%JDK%  & goto :fail )
if not exist "%PY%"   ( echo [错误] 找不到 Python 虚拟环境：%PY% & goto :fail )
if not exist "%NPM%"  ( echo [错误] 找不到 npm：%NPM% & goto :fail )
if not exist "%JARDIR%\%JAR%" ( echo [错误] 找不到后端 jar：%JARDIR%\%JAR% & goto :fail )

rem ---------- 1. AI 服务 ----------
echo [1/3] AI 服务        端口 8090
curl -s -m 3 http://localhost:8090/health >nul 2>&1
if %errorlevel%==0 (
  echo       已在运行，跳过
) else (
  start "queqiao-ai" /d "%AIDIR%" /min "%PY%" -m uvicorn main:app --host 127.0.0.1 --port 8090
  call :wait http://localhost:8090/health 30
)

rem ---------- 2. 后端 ----------
echo [2/3] 后端 SpringBoot 端口 8080
curl -s -m 3 http://localhost:8080/api/health >nul 2>&1
if %errorlevel%==0 (
  echo       已在运行，跳过
) else (
  start "queqiao-backend" /d "%JARDIR%" /min "%JDK%" -jar "%JAR%" --server.port=8080
  call :wait http://localhost:8080/api/health 45
)

rem ---------- 3. 前端 ----------
echo [3/3] 前端 H5        端口 5173
curl -s -m 3 http://localhost:5173/ >nul 2>&1
if %errorlevel%==0 (
  echo       已在运行，跳过
) else (
  start "queqiao-web" /d "%FEDIR%" /min cmd /c ""%NPM%" run dev:h5"
  call :wait http://localhost:5173/ 40
)

rem ---------- 自检 ----------
echo.
echo ============================================================
echo   自检结果
echo ============================================================
echo   AI 服务  :
curl -s -m 5 http://localhost:8090/health
echo.
echo   后端     :
curl -s -m 5 http://localhost:8080/api/health
echo.
echo   前端     :
curl -s -m 8 -o nul -w "             HTTP %%{http_code}\n" http://localhost:5173/
echo.
echo   访问地址 : http://localhost:5173/
echo   登录账号 : admin / 123456
echo ============================================================
echo.
echo 提示：三个服务运行在最小化窗口中，关闭窗口即停止对应服务。
echo       需要停止全部服务请运行 stop-queqiao.bat
echo.
pause
goto :eof

rem ---------- 等待服务就绪：%1=探测地址 %2=最大尝试次数 ----------
:wait
set /a __i=0
:waitloop
curl -s -m 3 %1 >nul 2>&1
if %errorlevel%==0 (
  echo       就绪
  exit /b 0
)
timeout /t 2 >nul
set /a __i+=1
if !__i! LSS %2 goto :waitloop
echo       [警告] 等待超时，请查看对应服务窗口的日志
exit /b 1

:fail
echo.
echo 启动中止：依赖缺失，请检查上面标注的路径。
echo.
pause
