Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$distDir = Join-Path $rootDir "dist"

if (Test-Path $distDir) {
    Remove-Item -Path $distDir -Recurse -Force
}
New-Item -ItemType Directory -Path $distDir | Out-Null

$profiles = @(
    @{ Name = "paper-1.18-1.20"; Property = "paper-1.18-1.20"; Output = "InvBackup-paper-1.18-1.20.jar" },
    @{ Name = "paper-1.21-plus"; Property = "paper-1.21-plus"; Output = "InvBackup-paper-1.21-plus.jar" }
)

foreach ($profile in $profiles) {
    Write-Host ("Building profile: {0}" -f $profile.Name)
    & (Join-Path $rootDir "gradlew.bat") clean build "-PbuildTarget=$($profile.Property)" -x test
    if ($LASTEXITCODE -ne 0) {
        throw ("Build failed for profile {0}" -f $profile.Name)
    }

    $jar = Get-ChildItem -Path (Join-Path $rootDir "build\libs\*.jar") -File |
            Select-Object -First 1
    if (-not $jar) {
        throw ("No jar produced for profile {0}" -f $profile.Name)
    }

    $target = Join-Path $distDir $profile.Output
    Copy-Item -Path $jar.FullName -Destination $target -Force
    Write-Host ("Created: {0}" -f $target)
}

Write-Host ""
Write-Host "Done. Artifacts:"
Get-ChildItem -Path $distDir -File | ForEach-Object { Write-Host (" - {0}" -f $_.Name) }
