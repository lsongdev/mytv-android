package me.lsong.mytv

import android.app.Application
import me.lsong.mytv.utils.Settings

class MyTVApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        UnsafeTrustManager.enableUnsafeTrustManager()
        Settings.init(applicationContext)
    }
}
