Write-Host "========================================"
Write-Host "InvBackup 多版本构建脚本"
Write-Host "========================================"
Write-Host ""

# 清理构建目录
if (Test-Path "build") {
    Remove-Item -Path "build" -Recurse -Force
}

$versions = @("1.21", "1.20", "1.19", "1.18")

foreach ($version in $versions) {
    Write-Host "构建 $version.x 版本..."
    
    # 构建指定版本
    .\gradlew.bat clean build "-PmcVersion=$version"
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "$version.x 构建失败！" -ForegroundColor Red
        exit 1
    }
    
    # 复制并重命名JAR文件
    $source = "build\libs\InvBackup-1.0.2.jar"
    $dest = "build\libs\InvBackup-$version.jar"
    
    if (Test-Path $source) {
        Copy-Item -Path $source -Destination $dest -Force
        Write-Host "已生成: $dest" -ForegroundColor Green
    }
    
    Write-Host ""
}

Write-Host "========================================"
Write-Host "构建完成！"
Write-Host "========================================"
Write-Host ""
Write-Host "生成的版本："
Get-ChildItem -Path "build\libs\InvBackup-*.jar" | ForEach-Object { $_.Name }

Write-Host ""
Write-Host "按任意键继续..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")