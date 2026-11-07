# 一次性环境初始化（便携版，无需管理员权限）
# 用法: powershell -ExecutionPolicy Bypass -File tools\setup-env.ps1
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$Root   = 'C:\Android'
$Sdk    = "$Root\Sdk"
$JdkDir = "$Root\jdk-17"
$Gradle = "$Root\gradle-8.9"

if (-not (Test-Path $JdkDir)) {
    Write-Host '解压 JDK 17...'
    Expand-Archive "$Root\dl\jdk17.zip" -DestinationPath $Root -Force
    $extracted = Get-ChildItem $Root -Directory | Where-Object { $_.Name -like 'jdk-17*' } | Select-Object -First 1
    if ($extracted -and $extracted.FullName -ne $JdkDir) { Move-Item $extracted.FullName $JdkDir -Force }
}

if (-not (Test-Path $Gradle)) {
    Write-Host '解压 Gradle 8.9...'
    Expand-Archive "$Root\dl\gradle-8.9-bin.zip" -DestinationPath $Root -Force
}

$env:JAVA_HOME = $JdkDir
$env:ANDROID_HOME = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk
$env:PATH = "$JdkDir\bin;$Gradle\bin;$Sdk\platform-tools;$Sdk\cmdline-tools\latest\bin;$env:PATH"

Write-Host "JAVA_HOME=$env:JAVA_HOME"
& "$JdkDir\bin\java.exe" -version

Write-Host '接受 SDK 许可...'
$yes = ("y`n" * 50)
$yes | & "$Sdk\cmdline-tools\latest\bin\sdkmanager.bat" --licenses | Out-Null

Write-Host '安装 platform-tools / platforms;android-35 / build-tools;35.0.0 ...'
& "$Sdk\cmdline-tools\latest\bin\sdkmanager.bat" --install "platform-tools" "platforms;android-35" "build-tools;35.0.0"

Write-Host '写入 local.properties ...'
$projectRoot = Split-Path -Parent $PSScriptRoot
"# generated`nsdk.dir=C\\:\\\\Android\\\\Sdk" | Set-Content -Path (Join-Path $projectRoot 'local.properties') -Encoding ASCII

Write-Host '完成。后续命令前请先执行:'
Write-Host '  $env:JAVA_HOME="C:\Android\jdk-17"; $env:ANDROID_HOME="C:\Android\Sdk"; $env:PATH="$env:JAVA_HOME\bin;C:\Android\gradle-8.9\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:PATH"'
