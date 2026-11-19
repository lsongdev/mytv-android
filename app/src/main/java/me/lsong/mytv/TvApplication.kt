package me.lsong.mytv

import me.lsong.mytv.app.AppContainer

import me.lsong.mytv.core.network.UnsafeTrustManager

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.lsong.mytv.data.remote.ConfigurationServer

class TvApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        UnsafeTrustManager.enableUnsafeTrustManager()
        AppContainer.initialize(applicationContext)
        applicationScope.launch { ConfigurationServer.start(applicationContext) }
        applicationScope.launch { AppContainer.providerStorage.initialize() }
    }
}
