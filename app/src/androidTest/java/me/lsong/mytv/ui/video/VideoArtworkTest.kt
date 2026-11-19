package me.lsong.mytv.ui.video

import me.lsong.mytv.ui.theme.TvTheme
import me.lsong.mytv.ui.video.VodArtwork

import me.lsong.mytv.domain.provider.ProviderRecordRef

import me.lsong.mytv.domain.video.ProviderVideoRecord
import me.lsong.mytv.domain.video.VideoWork

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp

import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.SocketException
import org.junit.Rule
import org.junit.Test

class VideoArtworkTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aFailedCoverFallsBackToAnotherProvidersImageAndRendersIt() {
        val image = ByteArrayOutputStream().apply {
            Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
                .compress(Bitmap.CompressFormat.PNG, 100, this)
        }.toByteArray()
        val server = ServerSocket(0)
        val worker = Thread {
            try {
                while (!server.isClosed) server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    val path = reader.readLine().split(' ')[1]
                    while (!reader.readLine().isNullOrEmpty()) { }
                    val body = if (path == "/good.png") image else byteArrayOf()
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 ${if (body.isNotEmpty()) "200 OK" else "404 Not Found"}\r\nContent-Type: image/png\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(body); flush()
                    }
                }
            } catch (_: SocketException) { }
        }.apply { isDaemon = true; start() }
        try {
            val base = "http://127.0.0.1:${server.localPort}"
            val work = VideoWork("cover-test", listOf("bad", "good").map { id ->
                ProviderVideoRecord(ProviderRecordRef(id, "1"), id, "测试封面", poster = "$base/$id.png")
            })
            compose.setContent { TvTheme { VodArtwork(work, Modifier.size(110.dp, 165.dp).testTag("artwork")) } }
            compose.waitUntil(10_000) {
                val artwork = compose.onNodeWithTag("artwork")
                val center = if (Build.VERSION.SDK_INT >= 26) {
                    val pixels = artwork.captureToImage().toPixelMap()
                    pixels[pixels.width / 2, pixels.height / 2]
                } else {
                    // Compose's Window PixelCopy overload requires API 26. Keep verifying
                    // rendered pixels on Android 7 using the instrumentation screenshot API.
                    val bounds = artwork.fetchSemanticsNode().boundsInWindow
                    val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                        ?: return@waitUntil false
                    try { Color(screenshot.getPixel(bounds.center.x.toInt(), bounds.center.y.toInt())) }
                    finally { screenshot.recycle() }
                }
                center.red > .95f && center.green < .05f && center.blue < .05f
            }
        } finally { server.close(); worker.join(2_000) }
    }
}
