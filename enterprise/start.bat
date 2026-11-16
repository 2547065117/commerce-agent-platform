@echo off
cd /d "%~dp0backend"
if not exist "target\enterprise-portal-1.0.0.jar" (
  echo Build enterprise first: powershell -File build.ps1
  pause
  exit /b 1
)
echo Console: http://127.0.0.1:8080
echo Initial credentials: backend\data\initial-admin.txt
java -jar target\enterprise-portal-1.0.0.jar
pause
