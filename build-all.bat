@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "ROOT_DIR=%~dp0"
set "DIST_DIR=%ROOT_DIR%dist"

echo ========================================
echo InvBackup dual-profile build
echo ========================================

if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
mkdir "%DIST_DIR%"

call :build_profile paper-1.18-1.20 InvBackup-paper-1.18-1.20.jar
if errorlevel 1 exit /b 1

call :build_profile paper-1.21-plus InvBackup-paper-1.21-plus.jar
if errorlevel 1 exit /b 1

echo.
echo Done. Artifacts:
dir /b "%DIST_DIR%\*.jar"
exit /b 0

:build_profile
set "PROFILE=%~1"
set "OUTPUT=%~2"
set "JAR_FILE="

echo.
echo Building profile: %PROFILE%
call "%ROOT_DIR%gradlew.bat" clean build "-PbuildTarget=%PROFILE%" -x test
if errorlevel 1 (
    echo Build failed for profile: %PROFILE%
    exit /b 1
)

for %%F in ("%ROOT_DIR%build\libs\*.jar") do (
    set "JAR_FILE=%%~fF"
    goto :jar_found
)

:jar_found
if not defined JAR_FILE (
    echo No jar produced for profile: %PROFILE%
    exit /b 1
)

copy /y "!JAR_FILE!" "%DIST_DIR%\%OUTPUT%" >nul
if errorlevel 1 (
    echo Failed to copy artifact for profile: %PROFILE%
    exit /b 1
)

echo Created: %DIST_DIR%\%OUTPUT%
exit /b 0
