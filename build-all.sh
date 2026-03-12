#!/bin/bash

echo "========================================"
echo "InvBackup 多版本构建脚本"
echo "========================================"
echo

echo "正在清理构建目录..."
rm -rf build

echo
echo "构建 1.21.x 版本..."
./gradlew clean build -PmcVersion=1.21
if [ $? -ne 0 ]; then
    echo "1.21.x 构建失败！"
    exit 1
fi
if [ -f "build/libs/InvBackup-1.0.2.jar" ]; then
    cp build/libs/InvBackup-1.0.2.jar build/libs/InvBackup-1.21.jar
    echo "已生成: build/libs/InvBackup-1.21.jar"
fi

echo
echo "构建 1.20.x 版本..."
./gradlew clean build -PmcVersion=1.20
if [ $? -ne 0 ]; then
    echo "1.20.x 构建失败！"
    exit 1
fi
if [ -f "build/libs/InvBackup-1.0.2.jar" ]; then
    cp build/libs/InvBackup-1.0.2.jar build/libs/InvBackup-1.20.jar
    echo "已生成: build/libs/InvBackup-1.20.jar"
fi

echo
echo "构建 1.19.x 版本..."
./gradlew clean build -PmcVersion=1.19
if [ $? -ne 0 ]; then
    echo "1.19.x 构建失败！"
    exit 1
fi
if [ -f "build/libs/InvBackup-1.0.2.jar" ]; then
    cp build/libs/InvBackup-1.0.2.jar build/libs/InvBackup-1.19.jar
    echo "已生成: build/libs/InvBackup-1.19.jar"
fi

echo
echo "构建 1.18.x 版本..."
./gradlew clean build -PmcVersion=1.18
if [ $? -ne 0 ]; then
    echo "1.18.x 构建失败！"
    exit 1
fi
if [ -f "build/libs/InvBackup-1.0.2.jar" ]; then
    cp build/libs/InvBackup-1.0.2.jar build/libs/InvBackup-1.18.jar
    echo "已生成: build/libs/InvBackup-1.18.jar"
fi

echo
echo "========================================"
echo "构建完成！"
echo "========================================"
echo
echo "生成的版本："
ls -la build/libs/InvBackup-*.jar