@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

rem ============================================================
rem  鹊桥·红娘工作台 —— 一键停止（AI 8090 / 后端 8080 / 前端 5173）
rem  用途：需要重启服务、或改完代码要冷启动时，先跑本文件释放端口。
rem ============================================================

echo ============================================================
echo   鹊桥·红娘工作台   一键停止
echo ============================================================
echo.

for %%P in (8090 8080 5173) do (
  echo  端口 %%P :
  call :killport %%P
)

echo.
echo ---------- 停止后复查 ----------
for %%P in (8090 8080 5173) do (
  set "__up=0"
  for /f "tokens=5" %%a in ('netstat -ano ^| findstr /C:":%%P " ^| findstr LISTENING') do set "__up=1"
  if "!__up!"=="0" ( echo   %%P  已释放 ) else ( echo   %%P  仍在监听 )
)
echo.
pause
goto :eof

rem ---------- 按端口杀进程 ----------
:killport
set "__killed=0"
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /C:":%1 " ^| findstr LISTENING') do (
  taskkill /F /PID %%p >nul 2>&1
  if !errorlevel!==0 (
    echo       已结束进程 PID %%p
    set "__killed=1"
  )
)
if "!__killed!"=="0" echo       未发现监听进程
exit /b 0
