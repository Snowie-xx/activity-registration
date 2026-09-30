@echo off
REM 启动 Redis（便携版），端口 6379。
setlocal
set REDIS=%~dp0..\..\env\redis\redis-server.exe
if not exist "%REDIS%" (
  echo [start-redis] redis-server.exe not found at %REDIS%
  echo   Please extract Redis-x64 zip to env\redis\ first.
  exit /b 1
)
echo [start-redis] starting redis-server on 6379 ...
start "redis" "%REDIS%" "%~dp0..\..\env\redis\redis.windows.conf"
echo [start-redis] started.
endlocal
