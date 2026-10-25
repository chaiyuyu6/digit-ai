@echo off
rem Start DigitLab GUI
setlocal
cd /d "%~dp0"
if not exist digit-ai.jar (
  call build.bat
  if errorlevel 1 exit /b 1
)
java -jar digit-ai.jar
