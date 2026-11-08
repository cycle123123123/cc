package com.jmreader.core

/**
 * 协议相关的全部常量集中在这里。
 * 官方升级换版本号/密钥时只改这个文件。
 * 详见 docs/API.md。
 */
object JmConfig {

    /** 参与 Token 签名与请求头，官方升级后需要同步。 */
    const val APP_VERSION = "2.1.12"

    /** 客户端声明适配的协议版本，用于更新提示。 */
    const val PROTOCOL_VERSION = APP_VERSION

    const val TOKEN_SECRET = "185Hcomic3PAPP7R"
    const val CONTENT_SECRET = "18comicAPPContent"
    const val HOST_LIST_KEY = "diosfjckwpqpdfjkvnqQjsik"

    const val DEFAULT_LANG = "TW"
    const val WEB_REFERER = "https://18comic.vip/"

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36"

    const val IMG_HOST_FALLBACK = "https://cdn-msp3.jmapiproxy1.cc"

    /** 图源切换：实测这些 CDN host 都能取到同一张图。 */
    val IMAGE_SHARDS = listOf(
        "cdn-msp.jmapiproxy1.cc",
        "cdn-msp2.jmapiproxy1.cc",
        "cdn-msp3.jmapiproxy1.cc",
        "cdn-msp.jmapiproxy3.cc",
    )

    val BUILTIN_HOSTS = listOf(
        "www.cdnbtd.cc",
        "www.cdnutc.cc",
        "www.cdngwc.net",
        "www.cdngwc.club",
    )

    val SERVER_LIST_URLS = listOf(
        "https://rup4a04-c02.tos-cn-hongkong.bytepluses.com/newsvr-2025.txt",
        "https://rup4a04-c01.tos-ap-southeast-1.bytepluses.com/newsvr-2025.txt",
        "https://rup4a04-c03.tos-cn-beijing.bytepluses.com.cn/newsvr-2025.txt",
    )

    /** 分条数的 aid 分段阈值（见 docs/API.md §6.2）。 */
    const val SCRAMBLE_RANGE_START = 268850L
    const val SCRAMBLE_RANGE_END = 421925L
    const val SCRAMBLE_LARGE_AID = 421926L
}
