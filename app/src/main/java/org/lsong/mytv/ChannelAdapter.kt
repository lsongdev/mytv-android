package org.lsong.mytv

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide

data class Source (
    val name: String,
    val url: String,
)

data class Channel (
    val id: Int,
    val name: String,
    val logo: String,
    val sources: List<Source>,
)

class ChannelAdapter(context: Context, private val channels: List<Channel>) : ArrayAdapter<Channel>(context, 0, channels) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val itemView = convertView ?: LayoutInflater.from(context).inflate(R.layout.channel_item, parent, false)

        // 获取数据项
        val channel = channels[position]

        // 获取布局中的视图并设置数据
        val logoView = itemView.findViewById<ImageView>(R.id.channel_logo)
        val nameView = itemView.findViewById<TextView>(R.id.channel_name)

        nameView.text = channel.name
        Glide.with(context)
            .load(channel.logo)
            .into(logoView)
        return itemView
    }
}
