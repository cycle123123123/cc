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

## D16 自动登录：Keystore 加密保存密码

- **决定**：勾选「記住密碼」时，用 Android Keystore 的 AES/GCM 密钥加密密码，
  密文存 SharedPreferences；token 过期或接口 401 时静默重登一次。
- **理由**：官方把账号密码明文放 localStorage；既然要做自动登录，至少不要落明文。
  密钥不可导出，换设备或清除应用数据后失效。
- **代价**：`SecureStore` 依赖 Android Keystore，JVM 单测覆盖不到，只能真机验证。

## D17 token 有效期读 JWT 的 exp

- **决定**：登录后解析 JWT payload 的 `exp` 作为本地过期时间，解析失败退回 1 小时。
- **理由**：实测官方 JWT 的 exp 约一年，官方前端却按 1 小时刷新。按 1 小时会导致
  每小时一次多余登录；按 exp 处理更接近真实有效期，401 时还有兜底重登。

## D18 「繼續閱讀」记录

- **决定**：阅读器每次翻页把 `(albumId, cid, title, page)` 写入 `ReadingHistory`
  单例（SharedPreferences + StateFlow），首页顶部显示卡片，点击直接进阅读器。
- **理由**：原来只按话记页码，用户必须先找到那本作品；首页入口省掉这一步。

## D19 runCatching 必须放过 CancellationException

- **问题**：切换收藏/歷史标签时，旧的 `LaunchedEffect` 被取消，`runCatching` 把
  Compose 的 `CancellationException` 当成业务错误，界面显示
  "The coroutine scope left the composition"。
- **决定**：UI 层的挂起调用统一用 `runCatchingCancellable`，取消异常原样抛出；
  错误文案统一走 `Throwable.userMessage()`，不把异常原文甩到界面上。
- **教训**：任何包住 suspend 调用的 `runCatching` 都有这个风险，不只是这一处。

## D20 收藏与收藏夹的真实参数

- **决定**：收藏用 `POST /favorite {aid}` 做 toggle，靠返回的 `type` 判断加/取消；
  收藏夹用 `POST /favorite_folder`，`type=add/edit/del/move`。
- **坑**：移动作品到收藏夹的参数是 **`aid`**；JS 里写的是 `nid`（那是小说的字段），
  照抄会一直「參數錯誤」。
- **坑**：删除收藏夹前必须先清空，空夹才能删。

## D21 评论接口字段

- **决定**：发表 `{comment, aid}`，回复再加 `comment_id`；删除 `{comment_id}`。
- **坑**：字段名是 `comment` 不是 `content`；回复没有 `parent_CID` 这种字段。

## D22 推荐位与作者接口

- **决定**：每週必看用 `/promote`（返回多块推荐位），实时推荐用 `/random_recommend`；
  作者列表 `/creator_author`，作者作品 `/creator_work?author_id=`。
- **坑**：两个 creator 接口的 `status` 类型不同（字符串 vs 数字），要分开建模。
- **未解决**：`hot_tags` 解密失败、`serialization` 参数未知、`tag_block` 一直 400/403。

## D24 阅读器细节与收藏排序- **阅读器设置面板**：亮度滑块（`Window.attributes.screenBrightness`，退出阅读器恢复系统值）、
  翻页动画开关（横向 Pager 按滚动偏移做 alpha + scale）、跳页滑块、书签（按话存
  `bookmarks_{cid}`，可跳转/移除）。
- **下一话预取**：看到最后 3 页时预取下一话前 3 页到 `cache/pages/{cid}/`，
  阅读时优先读该磁盘缓存，切话首屏不用等。
- **收藏排序/搜索**：接口不支持服务端排序（`o` / `keyword` 都返回 400），
  改为客户端对已加载收藏过滤/排序，并补上「載入更多」分页；页面会提示已载入多少条。
- **取舍**：收藏 1000+ 时想全局搜索需要本地索引全部收藏，暂未做。

## D23 加载速度优化

- **线路测速**：缓存线路可能是最慢的一条（实测 `www.cdnbtd.cc` 8.3s，
  `www.cdnutc.cc` 0.8s）。现在并发探测 5 条线路的 `/setting`（2.5s 超时），
  取最快并缓存 30 分钟；失败时自动重新发现并重试一次。
- **信任缓存线路**：启动时不再先拉线路清单 + 探测 setting，直接用缓存 base，
  失败才走发现流程，省掉两次网络往返。
- **首页缓存**：最近一次 `latest` 第一页写入 `home_cache.json`，冷启动先渲染旧数据，
  再后台刷新，避免长时间转圈。
- **Coil 复用 OkHttp + 200MB 磁盘缓存**：封面第二次打开直接命中磁盘。
- **详情页不阻塞**：`/album` 回来就结束 loading，评论区在另一个协程里加载。
- **会话内缓存**：`/album` 60 秒、`/comic_read` 5 分钟，返回再进不重复请求。

## D25 方向锁定与大图内存保护

- **方向锁定**：进入阅读器时按当前方向锁定（SENSOR_PORTRAIT / SENSOR_LANDSCAPE），
  离开时恢复 `UNSPECIFIED`；设置里可关闭。实测阅读器内强制旋转仍保持原方向，
  退出后可正常旋转。
- **大图内存保护**：解码采样同时看「屏幕宽度」和「总像素上限 6M」，
  极端长图（如 1080×8000）会自动降采样，避免单页位图 40MB+ 导致 OOM。
- **未做**：连页调整（跨页被拆成两张图时才有用）、纹理背景（在线漫画几乎看不到）、
  真·大图切分（先用总像素限制兜底）。
