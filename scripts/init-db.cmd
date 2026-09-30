@echo off
REM 初始化数据库：建库建表(schema.sql) + 固定数据(data.sql)
REM 前置：MySQL/MariaDB 已在 3306 端口运行。
REM 用法：init-db.cmd [用户名] [密码]
setlocal
set USERNAME=%1
set PASSWORD=%2
if "%USERNAME%"=="" set USERNAME=root

set MYSQL=%~dp0..\..\env\mariadb\mariadb-10.11.19-winx64\bin\mysql.exe
if not exist "%MYSQL%" set MYSQL=mysql

if "%PASSWORD%"=="" (
  "%MYSQL%" --default-character-set=utf8mb4 -u %USERNAME% < "%~dp0..\db\schema.sql"
  "%MYSQL%" --default-character-set=utf8mb4 -u %USERNAME% < "%~dp0..\db\data.sql"
) else (
  "%MYSQL%" --default-character-set=utf8mb4 -u %USERNAME% -p%PASSWORD% < "%~dp0..\db\schema.sql"
  "%MYSQL%" --default-character-set=utf8mb4 -u %USERNAME% -p%PASSWORD% < "%~dp0..\db\data.sql"
)
if errorlevel 1 (echo [init-db] FAILED & exit /b 1)
echo [init-db] OK: schema + data loaded
endlocal
