package com.snap.camerakit.sample.kotlin

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.snap.camerakit.common.Consumer
import com.snap.camerakit.lenses.LensesComponent

data class LensItem(val id: String, val groupId: String, val title: String?)

fun LensesComponent.Lens.toLensItem() = LensItem(id, groupId, name)

fun List<LensesComponent.Lens>.toLensItems(): List<LensItem> = map { it.toLensItem() }

class LensItemListAdapter(
    private val onItemClicked: Consumer<LensItem>
) : ListAdapter<LensItem, LensItemListAdapter.ViewHolder>(DIFF_CALLBACK) {

    constructor(onItemClicked: (LensItem) -> Unit) : this(Consumer { onItemClicked(it) })

    private var selectedPosition = 0

    fun select(lensItem: LensItem) {
        val position = currentList.indexOf(lensItem)
        if (position != -1) {
            val previousPosition = selectedPosition
            selectedPosition = position
            notifyItemChanged(selectedPosition)
            notifyItemChanged(previousPosition)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.lens_item, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindTo(getItem(position))
        holder.itemView.isSelected = selectedPosition == position
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view), View.OnClickListener {

        init {
            view.setOnClickListener(this)
        }

        private val title = view.findViewById<TextView>(R.id.title)

        @SuppressLint("SetTextI18n")
        fun bindTo(lensItem: LensItem) {
            title.text = "${lensItem.id}${if (lensItem.title != null) " : ${lensItem.title}" else ""}"
        }

        override fun onClick(v: View) {
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                selectedPosition = position
                notifyDataSetChanged()
                onItemClicked.accept(getItem(position))
            }
        }
    }

    companion object {

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<LensItem>() {

            override fun areItemsTheSame(oldItem: LensItem, newItem: LensItem) = oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: LensItem, newItem: LensItem) = oldItem == newItem
        }
    }
}
