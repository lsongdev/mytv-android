package me.lsong.mytv.data.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/** Package invalidation lives with the application, including while its menu is closed. */
class InstalledAppsObserver(context: Context, private val repository: InstalledAppsRepository) : AutoCloseable {
    private val context = context.applicationContext
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context,intent: Intent) { repository.refresh() }
    }
    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context,receiver,filter,ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
    }
    override fun close() { if (registered) { context.unregisterReceiver(receiver); registered = false } }
}
