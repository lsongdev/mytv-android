package org.lsong.mytv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

data class Source (
    val name: String,
    val url: String,
)

data class Channel (
    val name: String,
    val logo: String,
    val sources: List<Source>,
)

class ChannelAdapter(private val channels: Array<Channel>) :
    RecyclerView.Adapter<ChannelAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder (view) {
        val nameView: TextView
        val logoView: ImageView

        init {
            nameView = view.findViewById(R.id.channel_name)
            logoView = view.findViewById(R.id.channel_logo)
        }
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view  = LayoutInflater.from(viewGroup.context).inflate(R.layout.channel_item, viewGroup, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return channels.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.nameView.text = channels[position].name
        Glide.with(holder.logoView.context)
            .load(channels[position].logo)
            .into(holder.logoView)
    }
}