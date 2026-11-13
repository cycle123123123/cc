package com.jmreader.ui.common

/**
 * 「切回 tab 是否需要重新拉数据」的统一口径。
 *
 * 目标是三件事同时成立：
 * 1. 切 tab 秒开：状态挂在 AppRoot 上保活，不重建页面、不丢滚动位置；
 * 2. 不过度请求：数据还新鲜时一个请求都不发；
 * 3. 不会看到隔夜内容：超过新鲜期就在后台静默刷新，旧列表继续显示。
 *
 * 阈值取舍：
 * - 站点列表（首页最新、发现各 tab）按小时级更新，10 分钟足够新；
 * - 个人数据（收藏 / 云端历史）可能刚在别的设备或本页改过，5 分钟更跟手。
 */
object RefreshPolicy {

    /** 公共列表类数据的保鲜期。 */
    const val ListTtlMs = 10L * 60L * 1000L

    /** 收藏 / 历史等个人数据的保鲜期。 */
    const val LibraryTtlMs = 5L * 60L * 1000L

    /**
     * 上次加载时间未知（[lastLoadedAt] <= 0）一律视为过期；
     * 刚好等于 ttl 也算过期，保证「到点就刷」。
     */
    fun isStale(lastLoadedAt: Long, now: Long, ttlMs: Long): Boolean =
        lastLoadedAt <= 0L || now - lastLoadedAt >= ttlMs
}
