package com.miui.debloater

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DebloatAdapter(
    private var displayedItems: List<DebloatItem>,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<DebloatAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkBox: CheckBox = view.findViewById(R.id.cbItem)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvPkg: TextView = view.findViewById(R.id.tvPkg)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvDesc: TextView = view.findViewById(R.id.tvDesc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_debloat, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = displayedItems[position]
        holder.tvTitle.text = item.title
        holder.tvPkg.text = if (item.pkg.startsWith("__SYS_")) "系统底层安全配置项" else item.pkg
        holder.tvDesc.text = item.description

        if (item.installStatus.isNotEmpty()) {
            holder.tvStatus.visibility = View.VISIBLE
            holder.tvStatus.text = item.installStatus
            when {
                item.installStatus.contains("已精简") -> holder.tvStatus.setTextColor(Color.parseColor("#188038"))
                item.installStatus.contains("已冻结") -> holder.tvStatus.setTextColor(Color.parseColor("#E37400"))
                else -> holder.tvStatus.setTextColor(Color.parseColor("#5F6368"))
            }
        } else {
            holder.tvStatus.visibility = View.GONE
        }

        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = item.checked
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            item.checked = isChecked
            onSelectionChanged()
        }

        holder.itemView.setOnClickListener {
            item.checked = !item.checked
            holder.checkBox.isChecked = item.checked
            onSelectionChanged()
        }
    }

    override fun getItemCount(): Int = displayedItems.size

    fun updateList(newList: List<DebloatItem>) {
        displayedItems = newList
        notifyDataSetChanged()
    }
}
