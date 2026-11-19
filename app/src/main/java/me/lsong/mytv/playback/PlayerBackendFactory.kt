package me.lsong.mytv.playback

import me.lsong.mytv.playback.media3.Media3PlayerEngine
import me.lsong.mytv.playback.mpv.MpvPlayerEngine

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import me.lsong.mytv.data.preferences.AppPreferences

const val MPV_MIN_SDK = 26
fun effectivePlayerBackend(requested: String, sdk: Int): String =
    if (requested == "MPV" && sdk >= MPV_MIN_SDK) "MPV" else "MEDIA3"

object PlayerBackendFactory {
    fun create(context: Context, scope: CoroutineScope): PlayerEngine {
        if (Build.VERSION.SDK_INT >= MPV_MIN_SDK && effectivePlayerBackend(AppPreferences.playerBackend, Build.VERSION.SDK_INT) == "MPV") {
            try { return MpvPlayerEngine(context, scope) }
            catch (error: LinkageError) { android.util.Log.w("PlayerBackend", "MPV native library unavailable", error) }
            catch (error: Exception) { android.util.Log.w("PlayerBackend", "MPV initialization failed", error) }
        }
        return Media3PlayerEngine(context, scope)
    }
}
