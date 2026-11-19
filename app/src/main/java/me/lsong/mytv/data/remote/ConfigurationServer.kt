package me.lsong.mytv.data.remote

import me.lsong.mytv.app.AppContainer

import me.lsong.mytv.app.AppConstants
import me.lsong.mytv.data.preferences.AppPreferences

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.koushikdutta.async.AsyncServer
import com.koushikdutta.async.http.body.JSONObjectBody
import com.koushikdutta.async.http.server.AsyncHttpServer
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.lsong.mytv.domain.provider.ProviderConfig
import me.lsong.mytv.domain.provider.isHttpUrl
import me.lsong.mytv.R
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

object ConfigurationServer  {
    private const val SERVER_PORT = 10481
    private val json = Json { encodeDefaults = true }
    private var runningServer: AsyncHttpServer? = null
    val serverUrl: String by lazy {
        "http://${getLocalIpAddress()}:$SERVER_PORT"
    }
    @Synchronized
    fun start(context: Context) {
        if (runningServer != null) return
        val server = AsyncHttpServer()
        runningServer = server
        CoroutineScope(Dispatchers.IO).launch {
            try {

                server.get("/") { _, response ->
                    handleRawResource(response, context, "text/html", R.raw.index)
                }

                server.get("/api/settings") { _, response ->
                    handleGetSettings(response)
                }

                server.post("/api/settings") { request, response ->
                    handleSetSettings(request, response)
                }
                server.listen(AsyncServer.getDefault(), SERVER_PORT)
                Log.i("server", "服务已启动: 0.0.0.0:$SERVER_PORT")
            } catch (ex: Exception) {
                runningServer = null
                Log.e("server", "服务启动失败: ${ex.message}", ex)
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "设置服务启动失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun wrapResponse(response: AsyncHttpServerResponse) = response.apply {
        headers.set(
            "Access-Control-Allow-Methods", "POST, GET, DELETE, PUT, OPTIONS"
        )
        headers.set("Access-Control-Allow-Origin", "*")
        headers.set(
            "Access-Control-Allow-Headers", "Origin, Content-Type, X-Auth-Token"
        )
    }

    private fun handleRawResource(
        response: AsyncHttpServerResponse,
        context: Context,
        contentType: String,
        id: Int,
    ) {
        wrapResponse(response).apply {
            setContentType(contentType)
            send(context.resources.openRawResource(id).readBytes().decodeToString())
        }
    }

    private fun handleGetSettings(response: AsyncHttpServerResponse) {
        wrapResponse(response).apply {
            setContentType("application/json")
            send(
                json.encodeToString(
                    AllSettings(
                        appTitle = AppConstants.APP_NAME,
                        epgUrls = AppPreferences.epgUrls,
                        providers = AppContainer.providerConfigs.providers.value,
                    )
                )
            )
        }
    }

    private fun handleSetSettings(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
    ) {
        try {
            val body = request.getBody<JSONObjectBody>().get().toString()
            val update = json.decodeFromString<SettingsUpdate>(body)
            update.epgUrls?.forEach { require(isHttpUrl(it)) { "节目单地址必须为 HTTP 或 HTTPS 地址" } }
            update.providers?.let { AppContainer.providerConfigs.replace(it) }
            update.epgUrls?.let { AppPreferences.epgUrls = it }
            wrapResponse(response).setContentType("application/json")
            response.send("{\"ok\":true}")
        } catch (error: Exception) {
            wrapResponse(response).code(400).setContentType("application/json")
            response.send(json.encodeToString(mapOf("error" to (error.message ?: "配置格式无效"))))
        }
    }

    private fun getLocalIpAddress(): String {
        val defaultIp = "0.0.0.0"
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addr = iface.inetAddresses
                while (addr.hasMoreElements()) {
                    val inetAddress = addr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress ?: defaultIp
                    }
                }
            }
            return defaultIp
        } catch (ex: SocketException) {
            Log.e("server", "IP Address: ${ex.message}", ex)
            return defaultIp
        }
    }
}

@Serializable
private data class AllSettings(
    val appTitle: String,
    val epgUrls: Set<String>,
    @kotlinx.serialization.SerialName("sources") val providers: List<ProviderConfig>,
)
@Serializable
private data class SettingsUpdate(
    @kotlinx.serialization.SerialName("sources") val providers: List<ProviderConfig>? = null,
    val epgUrls: Set<String>? = null,
)
