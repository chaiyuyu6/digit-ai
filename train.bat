@echo off
rem Train the model from command line. Example: train.bat --epochs 8 --hidden 64,32
setlocal
cd /d "%~dp0"
if not exist out\classes\digitai\Main.class (
  call build.bat
  if errorlevel 1 exit /b 1
)
java -Dstdout.encoding=UTF-8 -cp out\classes digitai.Main train %*
