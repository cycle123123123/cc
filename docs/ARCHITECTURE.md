# 代码结构

最近一次整理：0.3.0（拆 `JmRepository`、拆四个大屏文件）。

## 目录

```
app/src/main/java/com/jmreader/
├── MainActivity.kt        单 Activity，注册音量键转发给阅读器
├── JmApplication.kt       Coil ImageLoader（复用仓库的 OkHttp / 磁盘缓存）
├── core/                  纯 JVM，可单测
│   ├── JmConfig            常量：内置线路、图源、UA、分条阈值
│   ├── JmCrypto            Token / Tokenparam / AES-ECB 解密
│   ├── ScrambleParams      图片分条数量与顺序
│   ├── ImageDescrambler    按分条还原位图
│   ├── HttpAwait           OkHttp Call → 可取消挂起函数
│   └── SafeCall            runCatchingCancellable（不吞 CancellationException）
├── data/
│   ├── JmRepository        业务入口：线路选择、会话、缓存、各接口（635 行）
│   ├── JmHttp              信封层：拼参数、签名头、解密（无状态）
│   ├── HostBenchmark       并发探测 /setting 选最快线路
│   ├── PageLoader          阅读页：取字节 → 采样解码 → 分条还原
│   ├── PageCache           页字节磁盘缓存（200MB LRU）
│   ├── PageStore           阅读器内存位图 LRU（48MB，预取并发 2）
│   ├── AuthStore           登录态（JWT/session/Keystore 密码）
│   ├── SecureStore         Android Keystore AES/GCM
│   ├── DownloadRepository  离线下载队列（应用内协程，不用 WorkManager）
│   ├── ContentFilter       屏蔽规则 + 预编译 Matcher
│   ├── SearchHistory / ReadingHistory / SeriesParser
│   ├── UpdateChecker       GitHub release 检查、下载、root 静默安装
│   └── Models              33 个 DTO（kotlinx.serialization）
└── ui/
    ├── AppRoot             导航：3 tab + overlay 栈；更新弹窗
    ├── HomeState/SearchState/AuthorWorksState   跨组合保留的列表状态
    ├── HomeScreen          首页分区 + 下拉刷新 + 骨架屏
    ├── DiscoverScreen + DiscoverTabs       分類/標籤/週榜/推薦
    ├── SearchScreen        搜索（分类筛选/排序/点击量）
    ├── AuthorWorksScreen   作者作品（搜索兜底 + 分类/排序/点击量）
    ├── DetailScreen + DetailSections       详情主屏 + 头部/预览/目录/评论
    ├── ReaderScreen + ReaderSections       阅读器主屏 + 翻页/缩放组件
    ├── LibraryScreen + LibraryContent + LibraryLogin   「我的」主屏/列表/登录
    ├── DownloadsScreen / BlocksScreen
    ├── AlbumCard / CommonFilters / Format / Theme / ReaderKeyBus
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
DTO（kotlinx.serialization）
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

`app/src/test` 共 8 个文件 / 39 个用例：crypto、分条算法、解析、屏蔽匹配、页缓存、版本比较。
仓库层与 UI 暂无自动化测试。
