# JMComic3 客户端协议参考（实测 v2.1.13）

本文档基于对官方 APK `JMComic3v2.1.13.apk` 的静态分析，并对线上接口做了实测验证。
所有结论均可复现，参考实现见 `tools/jm_probe.py`。

> 说明：接口非公开、随时可能变更。本项目只做协议记录，不保证长期有效。

---

## 1. 官方 App 的构成

| 项 | 值 |
|---|---|
| 框架 | Capacitor（WebView 壳） |
| appId | `com.a7m3p9xv.t6qk2z8.app`（可任意改） |
| 前端 | React SPA，整包内置于 `assets/public` |
| 版本号 | `2.1.13`，参与签名计算，升级后需同步 |
| 原生插件 | app / filesystem / preferences + 音量控制 |

**结论**：官方 App 的性能瓶颈来自 WebView + 巨型 React 树 + 无原生图片缓存，
不是网络。原生客户端可以绕开全部前端开销。

---

## 2. 线路发现（动态 host）

App 启动时先取一份加密的线路清单，解出 API 域名后再拉业务接口。

三个下发地址（任一可用即可）：

```
https://rup4a04-c02.tos-cn-hongkong.bytepluses.com/newsvr-2025.txt
https://rup4a04-c01.tos-ap-southeast-1.bytepluses.com/newsvr-2025.txt
https://rup4a04-c03.tos-cn-beijing.bytepluses.com.cn/newsvr-2025.txt
```

解密方式：

- 算法：AES-128-ECB / PKCS7
- 密钥：`md5("diosfjckwpqpdfjkvnqQjsik")` 的十六进制字符串（32 字节，直接当 key 用）
- 明文 JSON：

```json
{
  "Setting": ["www.cdnbtd.cc", "..."],
  "Server":  ["www.cdnbtd.cc", "..."],
  "jm3_Server": [["www.cdnbtd.cc", "線路1"], ["www.cdnutc.cc", "線路2"]]
}
```

取 `jm3_Server` 的 host，拼成 `https://{host}/` 作为 API base。
官方客户端会在这些线路间测速选择，原生端建议：首次并发探测（GET `/setting`），
成功即用，结果缓存 1~24 小时，失败自动切换。

`/setting` 返回的 `img_host` 是图片 CDN 基址（见 §6）。

---

## 3. 请求签名

每个请求都要带两个头，值由**当前 Unix 秒级时间戳**和固定密钥算出：

```
Tokenparam: {ts},{VERSION}                  // 例: 1794022248,2.1.13
Token:      md5("{ts}{TOKEN_SECRET}")       // 小写 hex
```

其中：

```
TOKEN_SECRET = "185Hcomic3PAPP7R"
VERSION      = "2.1.13"
```

实现要点：`ts` 必须和后面解密响应时用的 `ts` **完全一致**。
建议每次请求现算，避免长时间运行后时间漂移导致解密失败。

可选头（登录后）：

```
Authorization: Bearer {jwt}
Cookie: AVS={memberInfo.s}
```

未登录也可访问列表、详情、章节、图片等只读接口。

---

## 4. 响应包体

统一信封：

```json
{ "code": 200, "data": "<base64 密文>" }
```

`data` 解密：

- 算法：AES-128-ECB / PKCS7
- key 依次尝试：
  1. `md5("{ts}185Hcomic3PAPP7R")`
  2. `md5("{ts}18comicAPPContent")`
- 明文是 UTF-8 JSON

广告接口（`ad_content_all` / `advertise_all`）用固定 key `md5("18comicAPPContent")`，不含时间戳。

`code != 200` 时通常表示参数错误 / 未登录 / 需要付费。

---

## 5. 业务接口

Base = `https://{host}/`，全部为 GET，自动追加 `lang=TW`（或其他语言码）。

| 用途 | 路径 | 主要参数 |
|---|---|---|
| 应用配置 | `setting` | — |
| 最新上架 | `latest` | `page` |
| 人气/推荐 | `promote_list` / `promote` | `page` |
| 搜索 | `search` | `search_query`, `page`, `order`, `category`, `o` |
| 分类 | `categories`、`categories/filter` | |
| 每周排行 | `week`、`week/filter` | |
| 漫画详情 | `album` | `id` |
| 章节信息 | `chapter` | `id` |
| 阅读（图片列表） | `comic_read` | `id`（章节 id） |
| 收藏 | `favorite` | `page`, `folder_id` |
| 历史 | `watch_list` | `page` |
| 评论 | `forum`、`comment` | |
| 登录 | `login` | POST，FormData |
| 注册/找回 | `register` / `confirm` / `forgot` | POST |
| 签到 | `daily` / `daily_chk` | |

完整端点表见官方前端 `assets/public/static/js/*.js` 中 `6229` 模块（本文档末尾附摘录）。

### 5.1 `/latest`

返回数组，元素：

```json
{
  "id": "1489023",
  "name": "...",
  "author": "...",
  "category": {"id": "1", "title": "同人"},
  "category_sub": {"id": "1", "title": "同人"},
  "liked": false,
  "is_favorite": false,
  "update_at": 1794022251,
  "image": ""
}
```

**`image` 字段实测为空**，封面需要自己拼：

```
{img_host}/media/albums/{id}.jpg
{img_host}/media/albums/{id}_3x4.jpg   // 竖版缩略图
```

### 5.2 `/album?id={aid}`

返回对象，关键字段：

```json
{
  "id": 1489023,
  "name": "...",
  "description": "...",
  "total_photos": 39,
  "total_views": "6128",
  "addtime": "1793602446",
  "likes": 0,
  "series": [],
  "series_id": "0",
  "author": ["..."],
  "tags": ["巨乳", "NTR", "中文"],
  "works": [],
  "actors": [],
  "related_list": [ { "id": "32", "name": "...", "author": "...", "image": "" } ],
  "liked": false,
  "is_favorite": false,
  "price": 0,
  "purchased": false
}
```

- `total_views` 是**字符串**，点击量只在详情接口里出现；`latest` / `search` / `week/filter` / `categories/filter` 等列表接口都不返回它。
- `addtime` 是秒级时间戳字符串。
- `description` 经常是空串（尤其同人本），详情页需要自己补其它信息（页数/观看/点赞/标签）。

`series` 非空时表示是多话作品，元素含章节 id / 标题，可据此做目录。
单本作品的章节 id 等于 album id。

### 5.3 `/chapter?id={aid}`

```json
{
  "id": 1489023,
  "name": "...",
  "images": ["00001.webp", "...39 项..."],
  "series": 0,
  "tags": "巨乳 拍攝 NTR 中文",
  "addtime": "1794022248"
}
```

`images` 是文件名数组（不是完整 URL），实际地址由 `/comic_read` 给出。

### 5.4 `/comic_read?id={cid}`

```json
{
  "id": 1489023,
  "scramble_id": "220980",
  "total_page": 39,
  "images": [
    {"page": 1, "image": "https://cdn-msp3.jmapiproxy1.cc/media/photos/1489023/00001.webp?t=1794022251"}
  ]
}
```

- `scramble_id`：图片是否置乱、以及置乱参数的判定依据（见 §6）
- `images[].image`：可直接下载，无需再带签名头

### 5.5 多话作品的 `series`

`/album` 和 `/chapter` 都会返回 `series`，元素形如：

```json
{"id": "598174", "name": "", "sort": "1"}
```

实测要点：

1. **每个元素本身就是一个独立的 album id**，不是「章节号」。
2. `name` 通常为空字符串，目录标题要用 `sort` 生成（如「第 1 話」）。
3. 阅读某一话 = 用该元素的 `id` 调 `comic_read`。
4. **图片还原用的 `aid` 必须是 `comic_read` 返回的 `id`**，
   不是详情页的 album id。多话作品里二者不同，用错会导致分条数算错、图片错位。
5. `total_photos` 是整个系列的总页数，不是当前话的页数。

实测样本：`aid=598174`，`series` 219 项；`sort` 从 1 递增，但相邻两项的 id
不连续（例如第 23 项 `598196`、第 24 项 `1214150`），所以不能靠 id 推算顺序。

### 5.6 账号

登录是 **POST + FormData**：

```
POST /login?lang=TW
username=...
password=...
```

成功返回（字段已实测）：

```json
{
  "uid": "15429330",
  "username": "cycle1",
  "email": "...",
  "s": "gnju008at2l4ekvaaol5rcigqs",
  "jwttoken": "eyJhbGciOiJIUzI1NiIs...",
  "album_favorites": 1163,
  "level": 6,
  "coin": 7468
}
```

之后所有请求带：

```
Authorization: Bearer {jwttoken}
Cookie: AVS={s}
```

- 未登录或 token 过期访问需要鉴权的接口会返回 **HTTP 401**（不是 JSON code）。
- 官方前端把 token 有效期按 1 小时处理（`authExpiry = Date.now() + 3600_000`），
  到期后用它保存在 localStorage 的账号密码重新登录。
- `coin` 字段类型不稳定（有时数字有时字符串），解析时建议忽略或用宽松类型。

### 5.7 收藏与历史

收藏列表：

```
GET /favorite?page=1&folder_id={FID}
```

排序参数 `o` 和搜索参数在实测中都会返回 400（HTTP Bad Request），
只有 `page` 和 `folder_id` 可用。客户端只能对已加载的收藏做本地排序/过滤。

```json
{
  "list": [ { "id": "1215913", "name": "...", "author": "...", "category": {...} } ],
  "folder_list": [ { "FID": "4799712", "UID": "15429330", "name": "默认夹" } ],
  "total": "1163",
  "count": 20
}
```

收藏动作是 **toggle**：

```
POST /favorite   (FormData: aid=...)
```

返回 `{"status":"ok","msg":"...","type":"add"|"remove"}`，用 `type` 判断加还是取消。

收藏夹操作（同一个接口，按 `type` 区分）：

```
POST /favorite_folder
  type=add    folder_name=xxx
  type=edit   folder_id=xxx folder_name=yyy
  type=del    folder_id=xxx
  type=move   folder_id=xxx aid={作品id}
```

注意移动作品到收藏夹用的是 **`aid`**，不是 `nid`（用 nid 会返回「參數錯誤」）。
删除收藏夹前必须先把里面的作品移走（换夹用 move，或取消收藏再加回来）。

历史：

```
GET /watch_list?page=1
```

```json
{ "list": [ ... ], "total": 19 }
```

这里的 `total` 是**数字**。两个接口同名字段类型不同，别共用模型。

### 5.8 搜索

```
GET /search?search_query=test&page=1
```

```json
{
  "search_query": "test",
  "search_type": "site",
  "y": null,
  "m": null,
  "total": 48,
  "content": [ { "id": "1487787", "name": "...", "author": "...", "update_at": 1793606324 } ]
}
```

结果在 `content` 里，不是 `list`。`total` 是数字。

分类筛选用 **`c=<category slug>`**（不是 `category`）：

```
GET /search?search_query=test&c=doujin&page=1
```

实测 `test`：全部 48、`doujin` 24、`another` 24、`meiman` 69、`single`/`short`/`hanman` 0。
`category` / `cat` / `type` / `category_id` 等写法都会被忽略（返回和全部一样）。

用 `c=<slug>_<subslug>` 可以带子分类（和 `categories/filter` 一致）。

排序用 `o`：

```
GET /search?search_query=test&o=mv&page=1     # 最多點閱
GET /search?search_query=test&o=tf&page=1     # 最多愛心
```

`order=mv` / `order=tf` 无效（和默认顺序一样）；`o=mp` 会返回 500。

### 5.9 周榜

筛选选项：

```
GET /week
```

```json
{
  "categories": [ { "id": "265", "title": "", "time": "2026第264期11.06 - 10.30" } ],
  "type": [ { "id": "manga", "title": "日漫" } ]
}
```

榜单：

```
GET /week/filter?type=manga&id=265&page=1
```

```json
{ "total": 20, "list": [ { "id": "...", "name": "...", "author": "..." } ] }
```

- 期数参数是 **`id`**（`/week` 返回的 `categories[].id`）。传 `category` 会被忽略，导致「每期内容都一样」。
- `type`：`manga`（日漫）/ `another`（其他）。`hanman` 返回的内容和 `manga` 完全相同且 `title` 为空，客户端可以隐藏。
- `type` / `id` 可省略，省略时接口返回最新一期。
- 只有`週`榜，没有日榜/月榜：`/month`、`/rank` 等路径返回 `{"code":200,"data":[],"errorMsg":"Not legal.month"}`。

### 5.10 评论

```
GET /forum?aid={aid}&page=1
```

```json
{
  "list": [
    {
      "CID": "11295452",
      "UID": "18508358",
      "username": "hell_tractor_",
      "nickname": "hell_tractor_",
      "content": "<div style='...'>评论正文</div>",
      "likes": "0",
      "addtime": "Nov 07, 2026",
      "parent_CID": "0"
    }
  ],
  "total": 24
}
```

注意：

- `content` 是 **HTML**，展示前要转纯文本。
- `likes` 是字符串。
- `CID` 是评论 id，`parent_CID != "0"` 表示回复。

发表评论 / 回复（需登录）：

```
POST /comment   (FormData: comment=内容&aid={aid}[&comment_id={父评论CID}])
```

注意字段名是 `comment`，不是 `content`。带 `comment_id` 就是楼中楼回复。

删除自己的评论：

```
POST /comment_delete   (FormData: comment_id={CID})
```

### 5.11 点赞

作品点赞是切换操作：

```
POST /like      (FormData: id={aid})
```

成功返回 `{"status":"success","msg":"點讚成功!"}`。
注意参数名是 `id`，不是 `aid`（用 `aid` 会 400）。

评论点赞：

```
POST /comment_vote   (FormData: id={cid})
```

该接口返回体可能为空，只判断 HTTP 状态。

### 5.12 分类页数据

```
GET /categories
```

返回 `categories`（大类 + `sub_categories`）和 `blocks`：

```json
{ "title": "主題A漫", "content": ["劇情向", "校園", "純愛", "..."] }
```

分类浏览（参数从官方前端 `cateFilterList` 的 dispatch 里挖出来的）：

```
GET /categories/filter?c={slug}&o={order}&page=1
```

- `c` 必须是**顶级分类的 slug**：`doujin` / `single` / `short` / `another` / `hanman` / `meiman` …
  传数字 id、子分类 slug 都会被忽略（返回随机默认列表）。
- `o`：`""` 最新 / `mv` 最多點閱 / `mp` 最多圖片 / `tf` 最多愛心 / `old` 最舊。
- 返回 `{ "search_query": "", "total": 2259, "content": [...] }`。
- 实测：`c=hanman` 结果全部是韓漫，`total=2259`；`c=doujin` 全部同人。

`blocks` 里的标签可以直接作为 `search_query` 搜索。

子分类：把 slug 拼成 `{slug}_{sub_slug}` 传给 `c`，例如 `c=doujin_chinese`。

### 5.14 推荐 / 随机 / 作者

首页推荐位（每週必看等）：

```
GET /promote
```

返回数组，每项是一块推荐位：

```json
{ "id": "26", "title": "連載更新·...", "type": "promote", "filter_val": "26",
  "content": [ { "id": "1221144", "name": "...", "author": "..." } ] }
```

随机推荐：

```
GET /random_recommend    ->  [ 30 本作品 ]
```

作者列表 / 作者作品：

```
GET /creator_author?page=1
-> { "status": "200", "data": { "total": "20931",
     "content": [ { "id": "7118", "author_name": "...", "author_avatar": "/media/..." } ] } }

GET /creator_work?author_id={作者id}&page=1
-> { "status": 200, "data": { "total": "...",
     "content": [ { "id": "1100557", "work_title": "...", "work_image": "/media/..." } ] } }
```

注意两个接口的 `status` 类型不同（字符串 vs 数字）；`creator_work.content[].id` 就是作品 id。

**未确认**：

- `hot_tags` 的 `data` 用现有两把密钥都解不出来（返回乱码），暂不使用；
- `serialization`（连载专区）传 `page` / `type` 都返回「參數錯誤」；
- `tag_block`（标签屏蔽）GET 一律 400，POST 403。

### 5.13 图源切换

官方设置里有「圖源1~4」，对应 `setting.app_shunts`。
实测 CDN host 可以直接替换，9 个 host 都能取到同一张图：

```
cdn-msp.jmapiproxy1.cc
cdn-msp2.jmapiproxy1.cc
cdn-msp3.jmapiproxy1.cc
cdn-msp.jmapiproxy2.cc / cdn-msp2... / cdn-msp3...
cdn-msp.jmapiproxy3.cc / cdn-msp2... / cdn-msp3...
```

客户端做法：保留接口返回的 path 和 query，只替换 host。

---

## 6. 图片还原（关键）

章节图是**纵向分条倒序**的，必须客户端还原。算法取自官方前端
`static/js` 中 `5412` 模块的 `wR` 函数，已实测验证。

### 6.1 是否需要还原

```
if (url 以 .gif 结尾)         -> 不还原
else if (aid < scramble_id)   -> 不还原
else                          -> 还原
```

`aid` = 作品 id（数值比较）。

### 6.2 分条数量

```
key   = 图片文件名去掉扩展名，例如 URL 里的 "00001"
h     = md5("" + aid + key)          // hex 字符串
r     = ord(h 的最后一个字符)          // 注意是字符的 ASCII 码，不是数值
if 268850 <= aid <= 421925: r %= 10
else if aid >= 421926:      r %= 8
switch (r) {
  0 -> 2; 1 -> 4; 2 -> 6; 3 -> 8; 4 -> 10;
  5 -> 12; 6 -> 14; 7 -> 16; 8 -> 18; 9 -> 20;
  default -> 10
}
t = 结果
```

注意：

- `key` 是**每张图不同**的，所以同一章内不同页的分条数可能不同。
- `r` 未命中 0~9 时（aid < 268850 的情况）取默认值 10。

### 6.3 还原步骤

设图片尺寸 `W × H`，分条数 `t`：

```
h = H / t
u = H % t

源图从顶部切分：
  第 0 .. t-2 条：高度 h
  第 t-1 条：高度 h + u

目标图从顶部排列：
  第 0 条：高度 h + u
  第 1 .. t-1 条：高度 h

目标第 i 条 = 源图第 (t-1-i) 条
```

也就是把源图的分条顺序整体倒过来，同时把余数 `u` 归到最上面一条。

等价实现（Android）：用 `Canvas.drawBitmap(src, srcRect, dstRect, null)` 逐条绘制，
或先用 `Bitmap.createBitmap` 裁剪再拼接。

### 6.4 实测验证

对 `aid=1489023` 的前 5 页，按上述公式算出 `t`，还原后计算分条接缝处的行间差异：

| 页 | 文件名 | 公式 t | 还原后接缝差 | 页面平均行差 |
|---|---|---|---|---|
| 1 | 00001 | 2 | 38.7 | 26.5 |
| 2 | 00002 | 8 | 51.8 | 20.3 |
| 3 | 00003 | 10 | 26.5 | 25.8 |
| 4 | 00004 | 12 | 21.0 | 22.4 |
| 5 | 00005 | 10 | 16.6 | 19.7 |

接缝差 ≈ 页面平均行差，说明分条已还原到连续状态；
穷举 `t=2..25` 时只有公式给出的 `t` 能达到该水平（其余普遍 50~90）。

---

## 7. 原生实现注意事项

1. **时间戳一致性**：请求头的 `ts` 必须与解密用的 `ts` 相同，建议封装成一个
   `signedGet(path)`，内部一次性算好。
2. **线路缓存**：`newsvr` 是对象存储上的静态文件，不要每次启动都拉；缓存到本地，
   失败再刷新。
3. **图片解码**：先按屏幕宽度算 `inSampleSize` 再解码，还原在降采样后的图上做，
   避免大图全尺寸还原导致 OOM。还原后再上屏。
4. **预取**：阅读器预取后 2~3 页，配合磁盘缓存（OkHttp `Cache` 即可）。
5. **WebP**：章节图是 `.webp`，`BitmapFactory` 原生支持（API 14+）。
6. **图片防盗链**：实测带 `Referer: https://18comic.vip/` 更稳，建议加上。
7. **登录态**：`/login` 是 POST FormData，返回 jwt 与 `memberInfo`；之后请求要带
   `Authorization` 和 `Cookie: AVS=`。只读浏览不需要登录。

---

## 附：端点表（官方前端 6229 模块）

```
API_APP_SETTING              setting
API_ADVERTISE_ALL            ad_content_all
API_ADVERTISE_CONTENT_COVER  advertise_all
API_NOVEL_LIST               novels
API_NOVEL_DETAIL             novel
API_NOVEL_CHAPTERS           novelchapters
API_NOVEL_SEARCH             search_novels
API_NOVEL_LIKE               like
API_NOVEL_FAVORITES          novel_favorites
API_COMIC_SEARCH             search
API_COMIC_HOT_TAGS           hot_tags
API_COMIC_RANDOM_RECOMMEND   random_recommend
API_COMIC_PROMOTE            promote
API_COMIC_LATEST             latest
API_COMIC_PROMOTE_LIST       promote_list
API_COMIC_SER_MORE_LIST      serialization
API_COMIC_CHAPTER            chapter
API_COMIC_DETAIL             album
API_COMIC_READ               comic_read
API_MEMBER_LOGIN             login
API_MEMBER_LOOUT             logout
API_MEMBER_REGISTER          register
API_MEMBER_REGISTER_CONFIRM  confirm
API_MEMBER_FORGOT            forgot
API_CATEGORIES_LIST          categories
API_CATEGORIES_FILTER_LIST   categories/filter
API_FORUM_LIST               forum
API_COMMENT_SEND             comment
API_COMMENT_VOTE             comment_vote
API_COMMENT_DETELE           comment_delete
API_CHECK_RECOMMEND_BN       check_recommend_book
API_FAVORITE_LIST            favorite
API_LIKE_DATA                like
API_HISTORY_LIST             watch_list
API_GAMES_LIST               allgames
API_VIDEOS_LIST              videos
API_LATEST_HANIME            latest_hanime
API_VIDEO_INFO               video
API_BAITU_CREATE_TOKEN       baitu-create-token
API_BLOGS_LIST               blogs
API_BLOG_INFO                blog
API_GAME_INFO                game
API_FAVORITE_FOLDER          favorite_folder
API_TASKS_LIST               tasks
API_TASKS_BUY_LIST           coin
API_ERROR_LOG                error_log
API_WEEK                     week
API_WEEK__FILTER_LIST        week/filter
API_ALBUM_DOWNLOAD           album_download_2
API_USEREDIT                 useredit
API_ADVERTISE                advertise
API_ADVERTISE_CONTENT        ad_content
API_DAILY                    daily
API_DAILY_CHECK              daily_chk
API_DAILY_LIST               daily_list
API_DAILY_LIST_FILTER        daily_list/filter
API_TAGS_FAVORITE            tags_favorite
API_TAGS_FAVORITE_UPDATE     tags_favorite_update
API_COIN_BUY_COMICS          coin_buy_comics
API_COIN_BUY_CHARGE          coin_buy_charge
API_AD_FREE                  ad_free
API_AD_FREE_PAY              payment
API_NOTIFICATIONS            notifications
API_NOTIFICATIONS_UNREAD     notifications/unreadCount
API_NOTIFICATIONS_SERTRACK   album_sertracking
API_NOTIFICATIONS_TRACK_LIST album_tracking
API_CREATOR_AUTHOR           creator_author
API_CREATOR_WORK             creator_work
API_CREATOR_WORK_DETAIL      creator_author_work
API_CREATOR_WORK_INFO        creator_work_info
API_CREATOR_WORK_INFO_DETAIL creator_work_info_detail
API_SUPPORT_REPORT           support_report
API_TAG_BLOCK_SETTING        tag_block
```
