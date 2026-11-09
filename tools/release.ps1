param(
    [string]$Notes = "常规更新"
)

# 发版脚本：构建 release APK → 推代码和 tag → 建 GitHub Release 并上传 APK
# token 存在 %USERPROFILE%\.config\jmreader\token.txt（不在仓库里）

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$owner = 'cycle123123123'
$repo = 'cc'
$assetName = 'jmreader.apk'
$tokenFile = Join-Path $env:USERPROFILE '.config\jmreader\token.txt'

if (-not (Test-Path $tokenFile)) { throw "缺少 token 文件: $tokenFile" }
$token = (Get-Content $tokenFile -Raw).Trim()

# 网络：系统代理可用就走代理，不可用就直连（这台机器两者经常切换）
$sysProxy = (Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -ErrorAction SilentlyContinue).ProxyServer
$proxyUsable = $false
if ($sysProxy) {
    if ($sysProxy -notmatch '^https?://') { $sysProxy = "http://$sysProxy" }
    $m = [regex]::Match($sysProxy, '://([^:]+):(\d+)')
    if ($m.Success) {
        $proxyUsable = (Test-NetConnection $m.Groups[1].Value -Port ([int]$m.Groups[2].Value) -WarningAction SilentlyContinue).TcpTestSucceeded
    }
}
if ($proxyUsable) {
    $env:HTTP_PROXY = $sysProxy
    $env:HTTPS_PROXY = $sysProxy
    Remove-Item Env:no_proxy -ErrorAction SilentlyContinue
    Remove-Item Env:NO_PROXY -ErrorAction SilentlyContinue
    Write-Host "==> 使用代理 $sysProxy"
} else {
    Remove-Item Env:HTTP_PROXY -ErrorAction SilentlyContinue
    Remove-Item Env:HTTPS_PROXY -ErrorAction SilentlyContinue
    $env:no_proxy = 'github.com,api.github.com,uploads.github.com'
    $env:NO_PROXY = $env:no_proxy
    [System.Net.WebRequest]::DefaultWebProxy = $null
    Write-Host '==> 代理不可用，直连 GitHub'
}

function AuthHeader {
    @{ Authorization = "token $token"; 'User-Agent' = 'jmreader-release' }
}

Push-Location $root
try {
    # 版本号从 build.gradle.kts 读
    $gradle = Get-Content 'app/build.gradle.kts' -Raw
    $version = [regex]::Match($gradle, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
    $tag = "v$version"
    Write-Host "==> 构建 v$version"

    $env:JAVA_HOME = 'C:\Android\jdk-17'
    $env:ANDROID_HOME = 'C:\Android\Sdk'
    $env:PATH = "C:\Android\jdk-17\bin;C:\Android\gradle-8.9\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
    & gradle :app:assembleRelease --console=plain | Select-String -Pattern '^e: |FAILED|BUILD'
    if ($LASTEXITCODE -ne 0) { throw '构建失败' }

    $apk = 'app/build/outputs/apk/release/app-release.apk'
    if (-not (Test-Path $apk)) { throw "找不到 APK: $apk" }

    Write-Host '==> 推送代码'
    $basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("x-access-token:$token"))
    git -c "http.extraheader=AUTHORIZATION: basic $basic" push origin HEAD:main | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'push 失败' }

    Write-Host "==> 打 tag $tag"
    git tag -f $tag | Out-Null
    git -c "http.extraheader=AUTHORIZATION: basic $basic" push -f origin $tag | Out-Host

    Write-Host '==> 创建 Release'
    $headers = AuthHeader
    $body = @{
        tag_name         = $tag
        name             = "JMReader $tag"
        body             = $Notes
        draft            = $false
        prerelease       = $false
    } | ConvertTo-Json

    # 已存在同名 release 就先删掉（重发）
    try {
        $existing = Invoke-RestMethod -Uri "https://api.github.com/repos/$owner/$repo/releases/tags/$tag" -Headers $headers
        if ($existing) {
            Invoke-RestMethod -Method Delete -Uri "https://api.github.com/repos/$owner/$repo/releases/$($existing.id)" -Headers $headers | Out-Null
            Write-Host "    已删除旧的 $tag release"
        }
    } catch { }

    $release = Invoke-RestMethod -Method Post -Uri "https://api.github.com/repos/$owner/$repo/releases" `
        -Headers $headers -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) `
        -ContentType 'application/json; charset=utf-8'

    Write-Host '==> 上传 APK'
    $upload = "https://uploads.github.com/repos/$owner/$repo/releases/$($release.id)/assets?name=$assetName"
    Invoke-RestMethod -Method Post -Uri $upload -Headers $headers `
        -ContentType 'application/vnd.android.package-archive' -InFile (Resolve-Path $apk) | Out-Null

    Write-Host ''
    Write-Host "发布页: https://github.com/$owner/$repo/releases/tag/$tag"
    Write-Host "固定下载: https://github.com/$owner/$repo/releases/latest/download/$assetName"
} finally {
    Pop-Location
}
