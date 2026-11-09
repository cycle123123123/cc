package com.jmreader.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.jmreader.data.JmRepository

class JmApplication : Application(), ImageLoaderFactory {

    /** 应用级单例集合，UI 通过 LocalAppContainer 取用。 */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        repository = container.repository
    }

    /**
     * 让 Coil 复用仓库的 OkHttp（共享连接池），并启用磁盘缓存。
     * 默认 Coil 会尊重服务端缓存头，这里强制缓存，减少封面重复下载。
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(container.repository.okHttpClient)
            .respectCacheHeaders(false)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            .build()

    companion object {
        lateinit var repository: JmRepository
            private set
    }
}
