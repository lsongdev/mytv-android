package org.lsong.mytv

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.widget.ListView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

val cctv1 = Channel(
    name = "CCTV 1",
    logo = "https://resources.yangshipin.cn/assets/oms/image/202306/d57905b93540bd15f0c48230dbbbff7ee0d645ff539e38866e2d15c8b9f7dfcd.png?imageMogr2/format/webp",
    sources = listOf(
        Source(
            name = "",
            url = "http://dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226231/index.m3u8"
        )
    )
)
val cctv2 = Channel(
    name = "CCTV 2",
    logo  = "https://resources.yangshipin.cn/assets/oms/image/202306/20115388de0207131af17eac86c33049b95d69eaff064e55653a1b941810a006.png?imageMogr2/format/webp",
    sources = listOf(
        Source(
            name = "",
            url = "http://dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226195/index.m3u8"
        )
    )
)
val cctv3 = Channel(
    name = "CCTV 3",
    logo  = "https://resources.yangshipin.cn/assets/oms/image/202306/7b7a65c712450da3deb6ca66fbacf4f9aee00d3f20bd80eafb5ada01ec63eb3a.png?imageMogr2/format/webp",
    sources = listOf(
        Source(
            name = "",
            url = "http://dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226397/index.m3u8"
        )
    )
)

val btv = Channel(
    name = "BTV",
    logo = "https://resources.yangshipin.cn/assets/oms/image/202306/f4f23633c578beea49a3841d88d3490100f029ee349059fa532869db889872c5.png?imageMogr2/format/webp",
    sources = listOf(
        Source(
            name = "",
            url = "http://ottrrs.hl.chinamobile.com/PLTV/88888888/224/3221225728/index.m3u8",
        )
    )
)

val category1 = Category(
    name = "央视",
    channels = listOf(
        cctv1.name,
        cctv2.name,
        cctv3.name,
        btv.name,
    )
)
val category2 = Category(
    name = "卫视",
    channels = listOf(
        cctv2.name,
        cctv3.name,
    )
)


val categories = arrayOf(
    category1,
    category2,
)

val channels = arrayOf(
    cctv1,
    cctv2,
    cctv3,
    btv,

    cctv1,
    cctv2,
    cctv3,

    cctv1,
    cctv2,
    cctv3,
)

class MainActivity : Activity() {

    private lateinit var player : ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        player = ExoPlayer.Builder(this).build()
        player.playWhenReady = true
        val playerView = findViewById<PlayerView>(R.id.player_view)
        playerView.player = player

        val categoryAdapter = CategoryAdapter(categories)
        val categoryView = findViewById<RecyclerView>(R.id.category_list);
        categoryView.layoutManager = LinearLayoutManager(this)
        categoryView.adapter = categoryAdapter

        val channelAdapter = ChannelAdapter(channels)
        val channelView = findViewById<RecyclerView>(R.id.channel_list);
        channelView.layoutManager = LinearLayoutManager(this)
        channelView.adapter = channelAdapter
        this.playChannel(cctv1);
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        player.pause()
        super.onPause()
    }

    override fun onResume() {
        player.play()
        super.onResume()
    }

    override fun onStop() {
        player.stop()
        super.onStop()
    }

    private fun play(url: String){
        val mediaItem = MediaItem.fromUri(url)
        player.setMediaItem(mediaItem)
        player.prepare()
    }
    private  fun playChannel(channel: Channel) {
        this.play(channel.sources[0].url)
    }
}
