package com.miauto.launcher

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CanbusBridge.start(this)
        try { Obd.start(this) } catch (_: Exception) {}
    }
}
