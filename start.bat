@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

rem ============================================================
rem  AgentHub 一键启动（Windows）
rem  需要 JDK 21。脚本会按顺序找：本目录 jdk-21* > JAVA_HOME > PATH
rem ============================================================

cd /d "%~dp0"

set "JAR="
for %%f in ("target\agenthub-*.jar") do (
  if exist "%%~ff" set "JAR=%%~ff"
)
if "%JAR%"=="" (
  echo [错误] 没找到 target\agenthub-*.jar
  echo        先打包：mvnw.cmd clean package -DskipTests
  pause
  exit /b 1
)

set "JAVA="
for /d %%d in ("jdk-21*") do (
  if exist "%%~fd\bin\java.exe" set "JAVA=%%~fd\bin\java.exe"
)
if "%JAVA%"=="" if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if "%JAVA%"=="" (
  for /f "delims=" %%p in ('where java 2^>nul') do if not defined JAVA set "JAVA=%%p"
)
if "%JAVA%"=="" (
  echo [错误] 没找到 java.exe，请安装 JDK 21 并设置 JAVA_HOME
  pause
  exit /b 1
)

"%JAVA%" -version 2>&1 | findstr /c:"21." >nul
if errorlevel 1 (
  echo [警告] 当前 java 可能不是 21，启动会报 UnsupportedClassVersionError
  "%JAVA%" -version
  echo.
)

if not exist "tmp" mkdir "tmp"

if not exist "tmp\chat-settings.json" if "%DEEPSEEK_API_KEY%"=="" (
  echo.
  echo [提示] 还没有配置 API Key。
  echo        启动后打开 http://127.0.0.1:8123/api/ ，点右上角「模型设置」填一个 key 并保存。
  echo        填过的 key 存在 tmp\chat-settings.json，下次启动直接用。
  echo.
)

echo ============================================================
echo  启动中，约 8 秒后可用
echo  Web 界面 : http://127.0.0.1:8123/api/
echo  接口文档 : http://127.0.0.1:8123/api/swagger-ui.html
echo  健康检查 : http://127.0.0.1:8123/api/health
echo  按 Ctrl+C 停止
echo ============================================================
echo.

"%JAVA%" -Djava.io.tmpdir=./tmp -Dfile.encoding=UTF-8 -jar "%JAR%"

pause
