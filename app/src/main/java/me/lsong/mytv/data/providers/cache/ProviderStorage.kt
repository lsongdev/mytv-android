package me.lsong.mytv.data.providers.cache

import android.content.SharedPreferences
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import me.lsong.mytv.domain.provider.ProviderConfig

@Serializable
data class ProviderHealth(
    val endpoint: String,
    val respondedAt: Long = 0,
    val successAt: Long = 0,
    val latencyMs: Long = 0,
    val failures: Int = 0,
    val retryAt: Long = 0,
    val error: String? = null,
    val cachedAt: Long = 0,
)
@Serializable data class ProviderCacheEntry<T>(val savedAt: Long, val value: T)
class ProviderBackoffException(val retryAt: Long) : IOException("源暂时不可用，已安排稍后重试")

/** Metadata only: immutable cache keys include endpoint and query; playback bytes never enter this store. */
class ProviderStorage(private val directory: File, private val preferences: SharedPreferences, private val now: () -> Long = System::currentTimeMillis) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var initialized = false
    private val _health = MutableStateFlow<Map<String,ProviderHealth>>(emptyMap())
    val health = _health.asStateFlow()

    suspend fun initialize() = withContext(Dispatchers.IO) { mutex.withLock {
        if (!initialized) {
            _health.value = runCatching { preferences.getString("PROVIDER_HEALTH_V1",null)?.let { json.decodeFromString<Map<String,ProviderHealth>>(it) } }.getOrNull().orEmpty()
            initialized = true
        }
    } }
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun file(provider: ProviderConfig, key: String) = File(directory, hash("${provider.id}|${provider.url}|$key") + ".json")
    fun state(provider: ProviderConfig): ProviderHealth? = health.value[provider.id]?.takeIf { it.endpoint == hash(provider.url) }
    suspend fun <T> cached(provider: ProviderConfig, key: String, serializer: KSerializer<T>, maxAge: Long = 7 * 86400_000L, maxBytes: Long = 8_000_000): ProviderCacheEntry<T>? = withContext(Dispatchers.IO) {
        val file = file(provider,key)
        if (!file.exists() || file.length() > maxBytes) return@withContext null
        runCatching { json.decodeFromString(ProviderCacheEntry.serializer(serializer),file.readText()) }
            .getOrNull()?.takeIf { now() - it.savedAt <= maxAge }
    }
    suspend fun <T> get(provider: ProviderConfig, key: String, serializer: KSerializer<T>, freshFor: Long = 600_000, force: Boolean = false, maxBytes: Long = 8_000_000, fetch: suspend () -> T): T {
        initialize()
        val old = cached(provider,key,serializer,maxBytes = maxBytes)
        val status = state(provider)
        if (!force && status != null && status.retryAt > now()) return old?.value ?: throw ProviderBackoffException(status.retryAt)
        if (!force && old != null && now() - old.savedAt < freshFor && status?.error == null) return old.value
        return try {
            val result = measure(provider,fetch)
            withContext(Dispatchers.IO) { try { mutex.withLock {
                directory.mkdirs()
                val text = json.encodeToString(ProviderCacheEntry.serializer(serializer),ProviderCacheEntry(now(),result))
                val bytesToWrite = text.toByteArray(Charsets.UTF_8)
                if (bytesToWrite.size <= maxBytes) {
                    val target = file(provider,key); val temp = File(directory,target.name + ".tmp")
                    temp.writeBytes(bytesToWrite); temp.renameTo(target)
                    val status = state(provider)
                    if (status != null) {
                        _health.value = health.value + (provider.id to status.copy(cachedAt = now()))
                        preferences.edit().putString("PROVIDER_HEALTH_V1",json.encodeToString(health.value)).apply()
                    }
                    var bytes = 0L
                    directory.listFiles()?.filter { it.extension == "json" }?.sortedByDescending(File::lastModified)?.forEachIndexed { i, f ->
                        bytes += f.length(); if (i >= 128 || bytes > 48_000_000) f.delete()
                    }
                }
            } } catch (_: IOException) { /* A cache write failure must not discard a fresh network result. */ } }
            result
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { old?.value ?: throw error }
    }
    suspend fun <T> measure(provider: ProviderConfig, fetch: suspend () -> T): T {
        initialize()
        val start = System.nanoTime()
        try {
            val result = fetch()
            record(provider,null,(System.nanoTime()-start)/1_000_000)
            return result
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            record(provider, when (error) {
                is java.io.InterruptedIOException -> "响应超时"
                is java.net.UnknownHostException -> "域名不可达"
                is java.net.ConnectException -> "无法连接"
                is javax.net.ssl.SSLException -> "TLS 连接失败"
                else -> Regex("HTTP [0-9]{3}").find(error.message.orEmpty())?.value ?: "请求或解析失败"
            },(System.nanoTime()-start)/1_000_000)
            throw error
        }
    }
    private suspend fun record(provider: ProviderConfig, error: String?, latency: Long) = withContext(Dispatchers.IO) { mutex.withLock {
        val old = state(provider)
        val failures = if (error == null) 0 else (old?.failures ?: 0) + 1
        val delay = if (failures == 0) 0 else (15000L * (1L shl (failures-1).coerceAtMost(6))).coerceAtMost(600_000)
        val status = ProviderHealth(hash(provider.url),now(),if (error == null) now() else old?.successAt ?: 0,latency,failures,if (delay == 0L) 0 else now() + delay,error,old?.cachedAt ?: 0)
        _health.value = health.value + (provider.id to status)
        preferences.edit().putString("PROVIDER_HEALTH_V1",json.encodeToString(health.value)).apply()
    } }
}
