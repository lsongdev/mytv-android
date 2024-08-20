package me.lsong.mytv

import android.app.Application
import me.lsong.mytv.utils.SP

class MyTVApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        UnsafeTrustManager.enableUnsafeTrustManager()
        AppGlobal.cacheDir = applicationContext.cacheDir
        SP.init(applicationContext)
    }
}
