@echo off
rem DigitLab - Windows build script (requires JDK 17+)
setlocal
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
  echo [ERROR] javac not found. Please install JDK 17+ and add it to PATH.
  exit /b 1
)

if exist out\classes rmdir /s /q out\classes
mkdir out\classes
dir /s /b src\main\java\*.java > out\sources.txt
echo Compiling...
javac -encoding UTF-8 -d out\classes @out\sources.txt
if errorlevel 1 (
  echo [ERROR] Compilation failed.
  exit /b 1
)

jar --create --file digit-ai.jar --main-class digitai.Main -C out\classes .
if errorlevel 1 (
  echo [ERROR] Packaging failed.
  exit /b 1
)

echo.
echo Build OK: digit-ai.jar
echo   GUI    : run.bat
echo   Train  : train.bat --epochs 8
endlocal
