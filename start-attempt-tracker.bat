@echo off
setlocal
cd /d "%~dp0"
if exist "attempt-tracker-1.2.1-all.jar" (
  java -ea -jar "attempt-tracker-1.2.1-all.jar" --developer-mode --disable-telemetry --profile attempt-tracker-dev
) else (
  call gradlew.bat run
)
if errorlevel 1 pause
