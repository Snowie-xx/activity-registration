@echo off
REM 编译并启动应用（需 JDK 17/21 + Maven，MySQL 与 Redis 已启动）。
REM 凭据可通过环境变量 DB_URL / DB_USERNAME / DB_PASSWORD 覆盖。
setlocal
set JAVA_HOME=%~dp0..\..\env\jbr
if not exist "%JAVA_HOME%\bin\java.exe" (
  REM 若未放置便携 JDK，则回退到系统 PATH 中的 java/mvn
  set JAVA_HOME=
)
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d "%~dp0.."
mvn spring-boot:run
endlocal
