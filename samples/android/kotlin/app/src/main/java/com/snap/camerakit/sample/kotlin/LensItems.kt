package com.snap.camerakit.sample.kotlin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.snap.camerakit.common.Consumer
import com.snap.camerakit.lenses.LensesComponent

data class LensItem(val id: String)

fun List<LensesComponent.Lens>.toLensItems(): List<LensItem> = map { LensItem(it.id) }

class LensItemListAdapter(
    private val onItemSelected: Consumer<LensItem>
) : ListAdapter<LensItem, LensItemListAdapter.ViewHolder>(DIFF_CALLBACK) {

    constructor(onItemSelected: (LensItem) -> Unit) : this(Consumer { onItemSelected(it) })

    private var selectedPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.lens_item, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindTo(getItem(position))
        holder.itemView.isSelected = selectedPosition == position
    }

    inner class ViewHolder(private val view: View) : RecyclerView.ViewHolder(view), View.OnClickListener {

        init {
            view.setOnClickListener(this)
        }

        private val title = view.findViewById<TextView>(R.id.title)

        fun bindTo(lensItem: LensItem) {
            title.text = lensItem.id
        }

        override fun onClick(v: View) {
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                selectedPosition = position
                notifyDataSetChanged()
                onItemSelected.accept(getItem(position))
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
