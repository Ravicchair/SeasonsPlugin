@echo off
setlocal
cd /d "%~dp0"

echo === Seasons plugin builder ===
echo.

where javac >nul 2>nul
if errorlevel 1 (
  echo Could not find the Java compiler ^(javac^). Normal Java is not enough, you need a JDK.
  echo Install JDK 25 from https://adoptium.net and run this file again.
  echo.
  pause
  exit /b 1
)

set "JAVAC="
for /f "delims=" %%i in ('where javac') do if not defined JAVAC set "JAVAC=%%i"
for %%i in ("%JAVAC%\..\..") do set "JAVA_HOME=%%~fi"

for /f "tokens=2 delims= " %%v in ('javac -version 2^>^&1') do set "JV=%%v"
for /f "tokens=1 delims=." %%m in ("%JV%") do set "JMAJOR=%%m"
echo Found JDK %JV%
if %JMAJOR% LSS 25 (
  echo.
  echo Your JDK is too old. This plugin needs JDK 25 ^(the same Java that Paper 26.2 needs^).
  echo Install JDK 25 from https://adoptium.net and run this file again.
  echo.
  pause
  exit /b 1
)

set "MVN_VERSION=3.9.9"
set "MVN_DIR=%~dp0.build\apache-maven-%MVN_VERSION%"
if not exist "%MVN_DIR%\bin\mvn.cmd" (
  echo Downloading build tools ^(one time only^)...
  if not exist "%~dp0.build" mkdir "%~dp0.build"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -UseBasicParsing -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MVN_VERSION%/apache-maven-%MVN_VERSION%-bin.zip' -OutFile '%~dp0.build\maven.zip'; Expand-Archive -Force '%~dp0.build\maven.zip' '%~dp0.build'"
  if errorlevel 1 (
    echo.
    echo Download failed. Check your internet connection and try again.
    pause
    exit /b 1
  )
)

echo.
echo Building the plugin. The first run downloads Paper's libraries and takes a few minutes...
echo.
call "%MVN_DIR%\bin\mvn.cmd" -B clean package
if errorlevel 1 (
  echo.
  echo BUILD FAILED. Copy the red ERROR lines above and send them to Claude.
  pause
  exit /b 1
)

copy /y "target\Seasons-1.0.0.jar" "Seasons.jar" >nul
echo.
echo ============================================================
echo Done! Your plugin is: %~dp0Seasons.jar
echo Put Seasons.jar into your server's "plugins" folder.
echo ============================================================
pause
