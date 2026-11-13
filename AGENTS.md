# AGENTS.md

## 工作区
- Android 项目在 `jmapp/`（**git 仓库根**）。工作区根目录其余内容（`comics/`、`tools/mtui/`、`deploy/`、`mockups/`、大量 `*.json/*.png`）与 App 无关，不要动。
- 文档：`jmapp/docs/API.md`（协议）、`docs/DECISIONS.md`（设计决策）、`docs/ARCHITECTURE.md`（分层，最新）。`jmapp/README.md` 是早期版本，内容已过时。

## 构建与测试
```powershell
$env:JAVA_HOME='C:\Android\jdk-17'; $env:ANDROID_HOME='C:\Android\Sdk'
$env:PATH="C:\Android\jdk-17\bin;C:\Android\gradle-8.9\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
cd D:\work\javdb\jmapp

gradle :app:assembleDebug
gradle :app:testDebugUnitTest
gradle :app:testDebugUnitTest --tests "com.jmreader.data.ContentFilterTest"
gradle :app:assembleRelease   # R8 开启；产物 app/build/outputs/apk/release/app-release.apk（~1.7MB）
```
- 本机用系统 `gradle`（8.9）比 `gradlew.bat` 快；wrapper 指向腾讯镜像。
- 包名 `com.jmreader`，minSdk 24 / targetSdk 35。

## 安装与调试
- 真机 `4cc0527b`（USB 经常掉线），模拟器 `emulator-5554`（AVD `jmtest`）。
- debug 与 release 签名不同：覆盖安装 release 会 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，必须先 `adb uninstall com.jmreader`（会丢本地数据）。
- 这台机器上 `uiautomator dump` 常出现坐标偏移或返回陈旧内容；需要精确点击时以 `screencap` 截图定位，或连续两次 dump 对比。

## 编码坑（会毁文件）
- PowerShell 5.1 用 `Get-Content`/`Set-Content` 改 `.kt` 会把 UTF-8 当 GBK 读，写回就是乱码/语法错误。改源码用 edit 工具，或 python `open(..., encoding='utf-8', newline='\n')`。
- `.ps1` 必须 **UTF-8 带 BOM**，且只能有一个 BOM（重复 BOM 会让 `param` 解析失败）。改完用 `powershell -NoProfile -File xxx.ps1` 验证一次。

## 发版（GitHub）
- 先改 `app/build.gradle.kts` 的 `versionCode`/`versionName`，再跑 `tools/release.ps1`（构建 → push → tag → 建 Release → 上传 `jmreader.apk`）。
- token 在 `%USERPROFILE%\.config\jmreader\token.txt`，**不在仓库**。仓库：`cycle123123123/cc`。
- 脚本会检测系统代理是否可用：可用走代理，不可用直连。`github.com` 直连常被 reset，`api.github.com` 通常可用。
- 若 git push 失败只能走 API 建 release，tag 会打在旧提交上；之后要 `git tag -f vX.Y.Z <sha>` + `git push -f origin vX.Y.Z`。
- 固定下载地址：`https://github.com/cycle123123123/cc/releases/latest/download/jmreader.apk`
- App 内 `UpdateChecker` 每 15 分钟检查 release；有 root 时 `su -c pm install -r` 静默安装，失败回退系统安装器。

## 架构要点
- `core/` 纯 JVM（签名、AES、分条算法、可取消请求）；`data/` 拆成 `JmRepository`（线路/会话/缓存/业务）、`JmHttp`（签名+信封解密）、`PageLoader`（取页/解码/还原）、`PageCache`（磁盘 200MB）、`PageStore`（位图 48MB）、`ContentFilter`、`UpdateChecker`。
- `ui/AppRoot`：3 个底部 tab + overlay 栈，**只渲染栈顶**；`SearchState` / `AuthorWorksState` 挂在 `Screen` 实例上，`HomeState` / `DiscoverState` / `LibraryState` 挂在 `AppRoot` 上，切 tab 复用列表、筛选与滚动位置。没有 ViewModel。
- 切回 tab 是否重拉由 `ui/common/RefreshPolicy` 决定：列表 10 分钟、收藏/历史 5 分钟；过期时静默刷新（旧列表继续显示），未过期一个请求都不发。
- 主题在 `ui/Theme.kt`（当前**强制浅色**；阅读器自带黑底，不依赖主题）。
- 大屏文件已拆分：`DetailScreen`+`DetailSections`、`ReaderScreen`+`ReaderSections`、`DiscoverScreen`+`DiscoverTabs`、`LibraryScreen`+`LibraryContent`+`LibraryLogin`。新增 UI 尽量放对应子文件。

## 协议易错点（细节见 docs/API.md）
- 请求头 `Token=md5(ts+"185Hcomic3PAPP7R")`、`Tokenparam="{ts},2.1.13"`；响应 AES-ECB，key 依次尝试两个 secret。
- 搜索分类用 `c=<slug>`（`category` 会被忽略）；排序用 `o=mv|tf`（`o=mp` 服务端 500）；`order=` 无效。
- 周榜期数用 `id`（不是 `category`）；`hanman` 与 `manga` 返回相同内容且标题为空；没有日榜/月榜接口。
- `total_views` 只在 `/album` 返回，列表接口没有——卡片可见时按需补（并发 2）。
- 图片为纵向分条乱序，条数由 `md5(aid+文件名)` 末位决定；列表接口不含该字段，阅读时来自 `comic_read`。

## 测试现状
- 39 个 JVM 用例：crypto、分条算法、目录解析、屏蔽匹配、页缓存、版本比较。UI 与仓库层无自动化测试。
- 改数据解析/工具类后至少跑 `:app:testDebugUnitTest`；改 UI 后建议装到模拟器截一张图确认。
