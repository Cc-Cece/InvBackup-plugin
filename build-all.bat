@echo off
echo ========================================
echo InvBackup 多版本构建脚本
echo ========================================
echo.

echo 正在清理构建目录...
if exist build (
    rmdir /s /q build
)

echo.
echo 构建 1.21.x 版本...
call gradlew clean build -PmcVersion=1.21
if %errorlevel% neq 0 (
    echo 1.21.x 构建失败！
    pause
    exit /b 1
)
if exist build\libs\InvBackup-1.0.2.jar (
    copy build\libs\InvBackup-1.0.2.jar build\libs\InvBackup-1.21.jar >nul
    echo 已生成: build\libs\InvBackup-1.21.jar
)

echo.
echo 构建 1.20.x 版本...
call gradlew clean build -PmcVersion=1.20
if %errorlevel% neq 0 (
    echo 1.20.x 构建失败！
    pause
    exit /b 1
)
if exist build\libs\InvBackup-1.0.2.jar (
    copy build\libs\InvBackup-1.0.2.jar build\libs\InvBackup-1.20.jar >nul
    echo 已生成: build\libs\InvBackup-1.20.jar
)

echo.
echo 构建 1.19.x 版本...
call gradlew clean build -PmcVersion=1.19
if %errorlevel% neq 0 (
    echo 1.19.x 构建失败！
    pause
    exit /b 1
)
if exist build\libs\InvBackup-1.0.2.jar (
    copy build\libs\InvBackup-1.0.2.jar build\libs\InvBackup-1.19.jar >nul
    echo 已生成: build\libs\InvBackup-1.19.jar
)

echo.
echo 构建 1.18.x 版本...
call gradlew clean build -PmcVersion=1.18
if %errorlevel% neq 0 (
    echo 1.18.x 构建失败！
    pause
    exit /b 1
)
if exist build\libs\InvBackup-1.0.2.jar (
    copy build\libs\InvBackup-1.0.2.jar build\libs\InvBackup-1.18.jar >nul
    echo 已生成: build\libs\InvBackup-1.18.jar
)

echo.
echo ========================================
echo 构建完成！
echo ========================================
echo.
echo 生成的版本：
dir build\libs\InvBackup-*.jar /b
echo.
pause