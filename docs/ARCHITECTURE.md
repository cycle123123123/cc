# 代码结构

最近一次整理：0.4.3 之后的结构性重构（按职责拆包、拆大文件，无行为改动）。

## 目录

```
app/src/main/java/com/jmreader/
├── app/                   单 Activity / Application 入口
│   ├── MainActivity        注册音量键转发给阅读器
│   └── JmApplication       Coil ImageLoader（复用仓库的 OkHttp / 磁盘缓存）
├── core/                  纯 JVM，可单测
│   ├── JmConfig            常量：内置线路、图源、UA、分条阈值
│   ├── JmCrypto            Token / Tokenparam / AES-ECB 解密
│   ├── ScrambleParams      图片分条数量与顺序
│   ├── ImageDescrambler    按分条还原位图
│   ├── HttpAwait           OkHttp Call → 可取消挂起函数
│   ├── Format              formatCount（万/億）
│   └── SafeCall            runCatchingCancellable（不吞 CancellationException）
├── data/
│   ├── JmRepository        业务入口：线路选择、会话、缓存、各接口
│   ├── net/                JmHttp（签名+解密）、HostBenchmark（探测最快线路）
│   ├── page/               PageLoader、PageCache（200MB）、PageStore（48MB 位图）
│   ├── local/              AuthStore、SecureStore、ContentFilter、ReadingHistory、
│   │                       SearchHistory、AlbumMeta（卡片角标 + 按需补齐）
│   ├── download/           DownloadRepository、DownloadTask
│   ├── update/             UpdateChecker（GitHub release / 下载 / root 安装）
│   └── model/              Models（DTO）、JmError、SeriesParser
└── ui/
    ├── theme/              Theme（Ios* 颜色与字体）
    ├── components/         AlbumCard、IosControls（胶囊+分段）、Chips（分类/排序）、ErrorState
    ├── common/             OnBottomReached、Format（日期/体积）
    ├── navigation/         AppRoot、Screen、UpdateDialog
    ├── home/               HomeScreen、HomeState、HomeSections（骨架屏/继续阅读/更新横幅）
    ├── discover/           DiscoverScreen、CategoryTab、TagTab、WeekTab、RecommendTab
    ├── search/             SearchScreen、SearchState、AuthorWorksScreen、AuthorWorksState
    ├── detail/             DetailScreen、DetailHeader、DetailChapters、DetailComments、DetailPreview
    ├── reader/             ReaderScreen、ReaderBars、ReaderPages、ReaderSettingsDialog、ReaderKeyBus
    └── library/            LibraryScreen、LibraryContent、LoginForm、SettingsGroup、
                            LocalHistory、DownloadsScreen、BlocksScreen
```

## 数据流

```
Compose 屏幕
   │ suspend 调用
   ▼
JmRepository ──► HostBenchmark（选线路）
   │  ├─ ensureReady / recoverBase（缓存线路、失败重发现）
   │  ├─ ensureSession / silentLogin（JWT 过期用记住的密码重登）
   │  └─ JmHttp.get/post（签名头 + AES 解密）
   ▼
DTO（kotlinx.serialization）∈ data/model
   ▼
屏幕状态（State / remember）

阅读：
PageStore（内存位图）→ PageLoader（下载目录 → PageCache → 网络 → 解码 → 还原）

封面：
Coil（OkHttp 共用连接池 + 200MB 磁盘缓存）

离线：
DownloadRepository（协程队列，写 files/downloads/{cid}/{page}.bin）
```

## 缓存

| 层 | 上限 | 归属 |
|---|---|---|
| OkHttp HTTP | 256 MB | `JmRepository` |
| Coil 磁盘 | 200 MB | `JmApplication` |
| 阅读页字节 | 200 MB | `PageCache` |
| 阅读位图 | 48 MB | `PageStore`（每话一个实例） |

## 导航与状态

`AppRoot` 维护 `mutableStateListOf<Screen>`，只渲染栈顶；`Screen.Search` / `Screen.AuthorWorks`
各自持有 `SearchState` / `AuthorWorksState`，因此从详情返回不会丢列表和滚动位置。
首页用常驻 `HomeState`。

没有 ViewModel：`android:configChanges` 已覆盖旋转，进程被杀后状态仍会丢（阅读进度有 prefs 兜底）。

## 发版

- `tools/release.ps1`：读版本号 → `assembleRelease` → push → 打 tag → 建 GitHub Release → 上传 APK
- token 放在 `%USERPROFILE%\.config\jmreader\token.txt`（不在仓库内）
- App 内 `UpdateChecker` 查 `releases/latest`（15 分钟缓存），下载后优先 `su` 静默安装

## 测试

`app/src/test` 共 9 个文件 / 49 个用例：crypto、分条算法、解析、屏蔽匹配、页缓存、版本比较、卡片角标。
仓库层与 UI 暂无自动化测试。
