# JMReader

18comic.vip / JMComic 的原生 Android 客户端（Kotlin + Jetpack Compose）。

不是对官方 WebView 壳的套皮，而是直接对接官方 App v2.1.12 使用的 JSON API：
原生列表、原生图片缓存、原生分条还原，用来解决官方客户端卡顿的问题。

---

## 当前状态

已完成（可运行的最小闭环）：

- 动态线路发现（解密 `newsvr-2025.txt`，自动测速选线，失败回退内置域名）
- 请求签名（`Token` / `Tokenparam`）与响应 AES-ECB 解密
- 首页最新列表 + 分页
- 作品详情 + 目录
- 多话作品目录解析（`series`）+ 上一话/下一话
- 阅读器（纵向滚动、按屏宽采样解码、逐页还原、OkHttp 磁盘缓存）
- 章节图分条还原（算法与官方一致，已用线上数据 + 模拟器双重验证）
- 搜索（分页，接口实测 48 条结果）
- 分類 / 週榜（分類按主题标签搜索；週榜走 `/week/filter`）
- 详情页评论列表 + 发表评论 + 作品点赞 + 评论点赞
- 图源切换（阅读器内，CDN host 替换，4 档）
- 登录 / 收藏 / 历史（只存 jwt 与会话，不保存密码）
- 阅读体验：纵向/横向切换、音量键翻页、点击全屏、阅读进度记忆、后 3 页预取、48MB 内存 LRU
- 离线下载：应用内队列、断点续传、暂停/继续、删除、断网读本地
- 20 个 JVM 单元测试（签名、解密、分条数、分条顺序、目录解析、返回结构）

未实现：

- 分类接口的精确筛选参数（`/categories/filter` 参数未确认，目前用标签搜索替代）
- 图片源自动测速（只能手动切）
- 更新检查与通知栏下载进度
- 阅读器缩放手势的真机验证

---

## 目录结构

```
jmapp/
├─ docs/
│  └─ API.md                  # 协议参考（签名、加密、端点、图片还原）
├─ tools/
│  └─ jm_probe.py             # 同协议的 Python 参考实现，可独立跑通
├─ app/src/main/java/com/jmreader/
│  ├─ core/
│  │  ├─ JmCrypto.kt          # 请求签名 + AES-ECB 解密
│  │  └─ ImageDescrambler.kt  # 章节图分条倒序还原
│  ├─ data/
│  │  ├─ JmRepository.kt      # 线路发现、请求封装、图片加载
│  │  └─ Models.kt
│  └─ ui/
│     ├─ AppRoot.kt           # 简单的页面状态机（无 navigation 依赖）
│     ├─ HomeScreen.kt
│     ├─ DetailScreen.kt
│     ├─ ReaderScreen.kt
│     └─ Theme.kt
└─ app/build.gradle.kts
```

---

## 构建

要求：

- Android Studio（Ladybug 或更新）
- JDK 17
- Android SDK 35

步骤：

1. Android Studio → Open → 选择 `jmapp` 目录。
2. 直接使用仓库自带的 `gradlew.bat`（Gradle 8.9 已配置在 `gradle/wrapper`）。
3. Run `app`。

命令行（生成 wrapper 之后）：

```bash
./gradlew :app:assembleDebug
```

### 本机已装好的环境（便携版，无需管理员权限）

| 组件 | 路径 |
|---|---|
| JDK 17 | `C:\Android\jdk-17` |
| Gradle 8.9 | `C:\Android\gradle-8.9` |
| Android SDK | `C:\Android\Sdk`（platform 35 / build-tools 35.0.0） |
| 模拟器 | AVD `jmtest`（android-35 google_apis x86_64） |

每次开新终端先设置：

```powershell
$env:JAVA_HOME='C:\Android\jdk-17'
$env:ANDROID_HOME='C:\Android\Sdk'
$env:PATH="C:\Android\jdk-17\bin;C:\Android\gradle-8.9\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
```

启动模拟器：

```powershell
C:\Android\Sdk\emulator\emulator.exe -avd jmtest -gpu swiftshader_indirect
```

> `gradle-wrapper.properties` 已指向腾讯云镜像（`mirrors.cloud.tencent.com`），
> 国内首次同步不必等 services.gradle.org。

---

## 用 Python 验证协议（不需要 Android 环境）

```bash
pip install pycryptodome pillow

cd jmapp
python tools/jm_probe.py hosts            # 看当前线路
python tools/jm_probe.py latest 1         # 最新列表
python tools/jm_probe.py read 1489023     # 图片列表 + scramble_id
python tools/jm_probe.py image 1489023 1489023 1 out.png   # 下载并还原第 1 页
```

`image` 子命令会按照 docs/API.md §6 还原并保存，可以直接确认算法是否正确。

---

## 性能设计

官方 App 慢的根因是「WebView 里跑整个 React 站 + 无原生图片缓存」。本项目对应做了四件事：

1. **不加载任何前端代码**：列表就是 `LazyVerticalGrid`，没有 DOM、没有虚拟滚动。
2. **两处缓存**：OkHttp 磁盘缓存（256MB，覆盖接口与图片）+ 内存位图按需持有。
3. **解码前采样**：先读图片头算 `inSampleSize`，按屏幕宽度解码，避免 1280×1800 全尺寸进内存。
4. **还原在降采样图上做**：分条拼接只操作小图，单页开销从几十毫秒降到个位数。

官方 App 首屏要串行做「拉线路清单 → 测速 → 拉 setting → 拉首页」，本项目把线路结果
持久化，正常情况下首屏只发一次请求。

---

## 后续可做

1. 登录（`/login`，FormData）→ 收藏 / 历史 / 签到
2. 离线下载：`WorkManager` + 分页任务队列 + 断点续传
3. 多话作品：按 `series` 生成目录并预取下一话
4. 图源切换：`setting.app_shunts` 的 4 个图源 + 测速
5. 阅读体验：横向翻页模式、音量键翻页、预取后 3 页、长图切分
6. 搜索 / 分类 / 每周排行页

---

## 说明

接口来自对官方 APK 的静态分析，属于非公开接口，随时可能变更；本项目只用于技术研究与
个人使用，内容版权归原权利人所有。
