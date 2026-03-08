package com.vesc0.heartratemonitor

import android.app.Application
import com.vesc0.heartratemonitor.data.local.PreferencesManager

class HeartRateMonitorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PreferencesManager.init(this)
    }
}
