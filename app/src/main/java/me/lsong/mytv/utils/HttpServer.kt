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
                        // epgUrls = SP.epgUrls,
                        // iptvSourceUrls = SP.iptvSourceUrls,
                        epgUrls = emptySet(),
                        iptvSourceUrls = emptySet(),
                        videoPlayerUserAgent = Settings.videoPlayerUserAgent,
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
        val iptvSourceUrl = body.get("iptvSourceUrl").toString()
        val epgXmlUrl = body.get("epgXmlUrl").toString()
        val videoPlayerUserAgent = body.get("videoPlayerUserAgent").toString()

        // if (SP.iptvSourceUrls != iptvSourceUrl) {
        //     SP.iptvSourceUrl = iptvSourceUrl
        //     IptvRepository().clearCache()
        // }
        //
        // if (SP.epgXmlUrl != epgXmlUrl) {
        //     SP.epgXmlUrl = epgXmlUrl
        //     EpgRepository().clearCache()
        // }
        //
        // SP.videoPlayerUserAgent = videoPlayerUserAgent

        wrapResponse(response).send("success")
    }
    private fun getLocalIpAddress(): String {
        val defaultIp = "0.0.0.0"

        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
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
    val videoPlayerUserAgent: String,
)