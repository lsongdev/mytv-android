@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package me.lsong.mytv.data.apps

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean

data class InstalledAppsSnapshot(
    val apps: List<InstalledApp> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val refreshedAt: Long = 0,
)

/** Keeps the last usable system scan across menu and Activity lifetimes. */
class InstalledAppsRepository(
    private val load: suspend () -> List<InstalledApp>,
    private val now: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val started = AtomicBoolean()
    private val revisions = MutableStateFlow(0L)
    private val mutable = MutableStateFlow(InstalledAppsSnapshot())
    val state = mutable.asStateFlow()
    fun start() {
        if (!started.compareAndSet(false,true)) return
        scope.launch {
            // StateFlow coalesces event bursts; an event during a scan always causes another scan.
            revisions.collect {
                mutable.value = state.value.copy(loading = true)
                try {
                    val apps = load()
                    mutable.value = InstalledAppsSnapshot(apps,refreshedAt = now())
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    mutable.value = state.value.copy(loading = false,error = "无法读取应用列表，请稍后重试",refreshedAt = now())
                }
            }
        }
    }
    fun refresh() { revisions.update { it + 1 } }
    fun menuOpened() { if (now() - state.value.refreshedAt >= FRESH_FOR && !state.value.loading) refresh() }
    override fun close() { scope.cancel() }
    private companion object { const val FRESH_FOR = 60_000L }
}
