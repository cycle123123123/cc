# 技术决策记录

## D1 原生客户端直连官方 JSON API

- **决定**：不包 WebView，不复刻官方 React 前端，直接调用 `apiUrl + 端点`。
- **理由**：官方卡顿的根因是 WebView + 巨型 React 树 + 无原生图片缓存，
  套壳无法解决；接口是明文 JSON（AES 加密包体），成本可控。
- **代价**：接口非公开，需自行维护协议适配。

## D2 Kotlin + Jetpack Compose，单模块起步

- **决定**：单 `app` 模块，包内分层 `core / data / ui`。
- **理由**：14 人日规模的项目，多模块是负担；`core` 里保持纯 JVM 代码，
  后续可无痛抽成 `:core` 模块。
- **约束**：`core` 不得引用 `android.*`（`ImageDescrambler` 除外），保证可单测。

## D3 加密与签名集中在 JmConfig / JmCrypto

- **决定**：版本号、两个密钥、线路密钥、UA、Referer 全部收敛。
- **理由**：官方升级换版本号时只改一处；也便于对比新旧 APK 的差异。

## D4 图片解码在采样后还原

- **决定**：先 `inJustDecodeBounds` 算 `inSampleSize`，解码到屏幕宽度，再分条还原。
- **理由**：1280×1800 的原图全尺寸还原需要约 9MB 临时位图，连续几页就会 OOM；
  采样后通常只有 1~2MB。
- **代价**：还原后的图无法再用于「保存原图」，下载功能需要单独走原图路径。

## D5 Base64 用 okio 而不是 android.util.Base64

- **决定**：`JmCrypto` 使用 `okio.ByteString.decodeBase64`。
- **理由**：`android.util.Base64` 在 JVM 单测里是 stub，会抛 "not mocked"。
- **代价**：需要先过滤空白字符（okio 比 android 的 decoder 严格）。

## D6 线路发现：缓存优先 + 并发探测（P2 落地）

- **决定**：本地缓存上次可用 base，启动直接用；失败再拉 `newsvr` 清单并逐个探测。
- **理由**：官方每次启动都串行「拉清单 → 测速 → 拉 setting」，是首屏慢的主因之一。

## D7 音量键翻页走 Activity 转发

- **决定**：Compose 层订阅 `ReaderKeyBus`，`MainActivity.onKeyDown` 转发音量键。
- **理由**：系统音量键不会稳定进入 Compose 焦点链；Activity 是最可靠的一层。
- **代价**：需要在阅读器可见时设置 `active`，否则会吞掉其他页面的音量键。

## D8 横向翻页只在双指时拦截手势

- **决定**：自定义 `awaitEachGesture`，只有 `changes.size >= 2` 时才处理缩放/平移。
- **理由**：`detectTransformGestures` 会消费单指拖动，导致 Pager 无法翻页。
- **代价**：单指不能平移已放大的图，只能用双指拖动；双击缩放仍可用。

## D9 进度按话保存

- **决定**：`progress_{cid}` 存 SharedPreferences，进入阅读器时恢复。
- **理由**：多话作品每话页数不同，按话存才不会串位。

## D10 离线下载不用 WorkManager

- **决定**：用应用内 `CoroutineScope` + `Job` 管理下载队列，暂停/继续由 Job 控制。
- **理由**：实测在模拟器上，JobScheduler 认为网络未 `VALIDATED`，任务一直停在
  `pending` 不执行（`dumpsys jobscheduler` 可见 unsatisfied）。个人项目没有必要为此
  引入前台服务，应用内队列更可控、更容易调试。
- **代价**：进程被杀后下载会中断；下次启动会把 running/pending 重置为 paused，
  用户手动继续。文件级断点续传保证不会重复下载。

## D11 只下载原始字节

- **决定**：存服务端返回的置乱图 `{page}.bin`，阅读时再还原。
- **理由**：和网络路径共用同一套解码逻辑；还原后的图体积大且不可逆。
- **代价**：离线阅读每页仍要跑一次 Canvas 还原（单页个位数毫秒，可接受）。

## D12 断网时的章节元数据

- **决定**：meta.json 保存 `scramble_id` / `totalPages`，网络失败时用
  `localComicRead()` 构造 `ComicRead`。
- **理由**：只缓存图片不够——`/comic_read` 拿不到时连页数都没有，阅读器直接报错。

## D13 分类筛选参数从 JS 反推

- **决定**：`/categories/filter` 用 `c`（顶级分类 slug）+ `o`（排序）+ `page`。
- **来源**：官方前端 `cateFilterList` 的 dispatch 是 `{page, o, c}`，不是常见的
  `category` / `cid`。猜参数时全部无效，读源码才定位到。
- **限制**：只支持顶级分类，子分类没有对应参数（传子分类 slug 会被忽略）。

## D14 首页状态提升到 AppRoot

- **决定**：`HomeState` 在 `AppRoot` 里 `remember`，列表数据、分页、滚动位置、更新提示都放进去。
- **理由**：原来 Home 离开组合就被销毁，返回时要重新请求，列表短暂为空。
  表现为「从详情返回后第一次点击没反应」，实际是点在了空列表上。
- **收益**：返回秒开、滚动位置保留、首击生效。

## D15 图源测速用 HEAD 并发

- **决定**：对 4 个 CDN host 并发 HEAD 同一张图，取耗时最短的，结果持久化。
- **理由**：只需要连通性和首字节延迟，不需要真的下载整张图。
- **注意**：测速依赖当前章节的第一张图 URL，所以入口放在阅读器里。
