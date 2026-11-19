package me.lsong.media.danmaku.internal

import okhttp3.Call

internal object DanmakuQueryContext { val current = ThreadLocal<RequestScope?>() }

internal class RequestScope {
    private val calls = mutableSetOf<Call>()
    private var cancelled = false
    @Synchronized fun register(call: Call) {
        if (cancelled) { call.cancel(); throw java.util.concurrent.CancellationException() }
        calls += call
    }
    @Synchronized fun unregister(call: Call) { calls -= call }
    @Synchronized fun cancel() { cancelled = true; calls.forEach { it.cancel() }; calls.clear() }
}
