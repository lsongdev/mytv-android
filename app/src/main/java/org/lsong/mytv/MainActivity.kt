package org.lsong.mytv

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.View.GONE
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.lsong.mytv.api.MyResponse
import org.lsong.mytv.api.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : Activity() {

    private lateinit var player : ExoPlayer
    private lateinit var channels: List<Channel>
    private var currentChannelIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        player = ExoPlayer.Builder(this).build()
        player.playWhenReady = true
//        player.addListener(object : Player.EventListener {
//
//        }

        val playerView = findViewById<PlayerView>(R.id.player_view)
        playerView.player = player

        val menuLayout = findViewById<LinearLayout>(R.id.menu_layout);
        menuLayout.visibility = GONE

        this.loadData()
    }
    private fun handleData(data: MyResponse) {
        val channelMap = data.channels.associateBy { it.name }
        val channelView = findViewById<ListView>(R.id.channel_list);
        channelView.setOnItemClickListener { parent, view, position, id ->
            this.playChannel(channels[position])
        }
        fun selectCategory(category: Category) {
            channels = category.channels.mapNotNull { channelMap[it] }
            channelView.adapter = ChannelAdapter(this, channels)
        }
        val categoryView = findViewById<RecyclerView>(R.id.category_list);
        categoryView.layoutManager = LinearLayoutManager(this)
        categoryView.adapter = CategoryAdapter(data.categories) { category ->
            // This code will be executed when a category item is clicked
            selectCategory(category)
        }
        selectCategory(data.categories[0])
        this.playChannel(channels[0])
    }
    private fun loadData() {
        val context = this
        RetrofitClient.apiService.getChannels().enqueue(object : Callback<MyResponse> {
            override fun onResponse(call: Call<MyResponse>, response: Response<MyResponse>) {
                if (response.isSuccessful) {
                    response.body()?.let {
                        context.handleData(it)
                        Log.d("MainActivity", "Response: ${it.toString()}")
                    }
                } else {
                    Log.e("MainActivity", "Error Response: ${response.errorBody()?.string()}")
                }
            }
            override fun onFailure(call: Call<MyResponse>, t: Throwable) {
                Log.e("MainActivity", "Network Request Failed", t)
            }
        })
    }

    private fun play(url: String){
        val mediaItem = MediaItem.fromUri(url)
        player.setMediaItem(mediaItem)
        player.prepare()
    }
    fun showChannelInfo(channel: Channel) {
        val channelInfo = findViewById<LinearLayout>(R.id.channel_info)
        val channelIdView = findViewById<TextView>(R.id.channel_id)
        val channelTitleView = findViewById<TextView>(R.id.channel_title)
        // channelIdView.text = channel.id.toString()
        channelTitleView.text = channel.name
        channelInfo.visibility = View.VISIBLE
        val handler = Handler(Looper.getMainLooper())
        val runnable = Runnable {
            channelInfo.visibility = View.GONE
        }
        handler.postDelayed(runnable, 3000)
    }
    private  fun playChannel(channel: Channel) {
        this.play(channel.sources[0].url)
        this.showChannelInfo(channel)
    }

    private fun changeChannel(direction: Int) {
        currentChannelIndex = (currentChannelIndex + direction + channels.size) % channels.size
        Log.d("MainActivity", "currentChannelIndex: $currentChannelIndex")
        val channel = channels[currentChannelIndex]
        this.playChannel(channel)
    }


    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val menuLayout = findViewById<LinearLayout>(R.id.menu_layout);
        when (keyCode) {
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_ENTER -> {
                if (menuLayout.visibility == View.GONE) {
                    menuLayout.visibility = View.VISIBLE
                    return true
                }
            }
            KeyEvent.KEYCODE_BACK -> {
                if (menuLayout.visibility == View.VISIBLE) {
                    menuLayout.visibility = View.GONE
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (menuLayout.visibility == View.VISIBLE) {
                    return super.onKeyDown(keyCode, event)
                }
                changeChannel(-1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (menuLayout.visibility == View.VISIBLE) {
                    return super.onKeyDown(keyCode, event)
                }
                changeChannel(1)
                return true
            }
        }
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
}
