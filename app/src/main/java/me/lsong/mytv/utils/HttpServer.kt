package me.lsong.mytv.utils

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
import me.lsong.mytv.R
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

object HttpServer  {
    private const val SERVER_PORT = 10481
    private var showToast: (String) -> Unit = { }
    val serverUrl: String by lazy {
        "http://${getLocalIpAddress()}:$SERVER_PORT"
    }
    fun start(context: Context, showToast: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val server = AsyncHttpServer()
                server.listen(AsyncServer.getDefault(), SERVER_PORT)

                server.get("/") { _, response ->
                    handleRawResource(response, context, "text/html", R.raw.index)
                }

                server.get("/api/settings") { _, response ->
                    handleGetSettings(response)
                }

                server.post("/api/settings") { request, response ->
                    handleSetSettings(request, response)
                }

                HttpServer.showToast = showToast
                Log.i("server", "服务已启动: 0.0.0.0:$SERVER_PORT")
            } catch (ex: Exception) {
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
                Json.encodeToString(
                    AllSettings(
                        appTitle = Constants.APP_NAME,
                        epgUrls = Settings.epgUrls,
                        iptvSourceUrls = Settings.iptvSourceUrls,
                    )
                )
            )
        }
    }

    private fun handleSetSettings(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
    ) {
        val body = request.getBody<JSONObjectBody>().get()
        try {
            val jsonObject = JSONObject(body.toString())
            val epgUrls = jsonObject.getJSONArray("epgUrls").let { array ->
                (0 until array.length()).map { array.getString(it) }.toSet()
            }
            val iptvSourceUrls = jsonObject.getJSONArray("iptvSourceUrls").let { array ->
                (0 until array.length()).map { array.getString(it) }.toSet()
            }

            // 保存设置
            Settings.epgUrls = epgUrls
            Settings.iptvSourceUrls = iptvSourceUrls

            // 显示提示信息
            CoroutineScope(Dispatchers.Main).launch {
                showToast("设置已保存")
            }

            wrapResponse(response).send("设置已成功保存")
        } catch (e: Exception) {
            wrapResponse(response).code(400).send("无效的JSON格式: ${e.message}")
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
    val iptvSourceUrls: Set<String>,
)