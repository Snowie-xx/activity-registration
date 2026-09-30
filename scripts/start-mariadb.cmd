@echo off
REM 启动 MariaDB（便携版）。首次运行自动初始化数据目录（root 无密码）。
REM 数据目录与二进制均在 ../../env/mariadb 下。
setlocal
set BASE=%~dp0..\..\env\mariadb\mariadb-10.11.19-winx64
set DATADIR=%BASE%\data
set MYSQLD=%BASE%\bin\mysqld.exe

if not exist "%MYSQLD%" (
  echo [start-mariadb] mysqld.exe not found at %MYSQLD%
  echo   Please extract mariadb to env\mariadb\ first.
  exit /b 1
)

if not exist "%DATADIR%\mysql" (
  echo [start-mariadb] initializing data dir ...
  "%BASE%\bin\mariadb-install-db.exe" --datadir="%DATADIR%" --auth-root-authentication-method=normal
)

echo [start-mariadb] starting mysqld on 3306 ...
start "mariadb" /D "%BASE%" "%MYSQLD%" --datadir="%DATADIR%" --port=3306 --console
echo [start-mariadb] started. Use mysql -u root to connect.
endlocal
