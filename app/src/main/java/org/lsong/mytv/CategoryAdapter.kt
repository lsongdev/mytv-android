package org.lsong.mytv

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

data class Category(
    val name: String,
    val channels: List<String>,
)

class CategoryAdapter(private val categories: List<Category>, private val onItemClick: (Category) -> Unit) :
    RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {
    private var selectedPosition = RecyclerView.NO_POSITION
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameView = itemView.findViewById<TextView>(R.id.category_name)
    }




    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view  = LayoutInflater.from(viewGroup.context).inflate(R.layout.category_item, viewGroup, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return categories.size
    }
    fun selectItem(position: Int) {
        if (selectedPosition != position) {
            val previousSelected = selectedPosition
            selectedPosition = position

            // 通知更改，以便重新绘制视图
            notifyItemChanged(previousSelected)
            notifyItemChanged(selectedPosition)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        holder.nameView.text = category.name
        holder.itemView.isSelected = position == selectedPosition
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
        }
        holder.itemView.setOnClickListener {
            selectItem(position)
            onItemClick(categories[position])
        }
    }
}