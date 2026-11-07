package com.jmreader

import android.app.Application
import com.jmreader.data.JmRepository

class JmApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        repository = JmRepository(this)
    }

    companion object {
        lateinit var repository: JmRepository
            private set
    }
}
