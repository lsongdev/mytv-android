package org.lsong.mytv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class Category(
    val name: String,
    val channels: List<Any>,
)

class CategoryAdapter(private val categories: Array<Category>) :
    RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder (view) {
        val nameView: TextView

        init {
            nameView = view.findViewById(R.id.category_name)
        }
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view  = LayoutInflater.from(viewGroup.context).inflate(R.layout.category_item, viewGroup, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return categories.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.nameView.text = categories[position].name
    }
}