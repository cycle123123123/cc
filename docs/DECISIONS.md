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
