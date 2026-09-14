package com.ewaste.formalization.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ewaste.formalization.R
import com.ewaste.formalization.data.local.entity.RecyclerEntity

class RecyclerAdapter(
    private val onRecyclerSelected: (RecyclerEntity) -> Unit
) : ListAdapter<RecyclerEntity, RecyclerAdapter.RecyclerViewHolder>(DiffCallback) {

    private var selectedPosition: Int = 0

    fun getSelectedRecycler(): RecyclerEntity? {
        if (currentList.isEmpty() || selectedPosition !in currentList.indices) return null
        return currentList[selectedPosition]
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recycler_card, parent, false)
        return RecyclerViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerViewHolder, position: Int) {
        holder.bind(getItem(position), position == selectedPosition) {
            val prevPos = selectedPosition
            selectedPosition = holder.bindingAdapterPosition
            notifyItemChanged(prevPos)
            notifyItemChanged(selectedPosition)
            onRecyclerSelected(getItem(selectedPosition))
        }
    }

    class RecyclerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRecyclerName: TextView = itemView.findViewById(R.id.tvRecyclerName)
        private val tvRecyclerCpcb: TextView = itemView.findViewById(R.id.tvRecyclerCpcb)
        private val tvRecyclerAddress: TextView = itemView.findViewById(R.id.tvRecyclerAddress)
        private val tvRecyclerCategories: TextView = itemView.findViewById(R.id.tvRecyclerCategories)
        private val rbSelectRecycler: RadioButton = itemView.findViewById(R.id.rbSelectRecycler)

        fun bind(recycler: RecyclerEntity, isSelected: Boolean, onClick: () -> Unit) {
            tvRecyclerName.text = recycler.companyName
            tvRecyclerCpcb.text = "CPCB Reg: ${recycler.cpcbRegistrationNumber}"
            tvRecyclerAddress.text = recycler.facilityAddress
            tvRecyclerCategories.text = "Authorized Fractions: ${recycler.authorizedCategories}"
            rbSelectRecycler.isChecked = isSelected

            itemView.setOnClickListener { onClick() }
            rbSelectRecycler.setOnClickListener { onClick() }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<RecyclerEntity>() {
        override fun areItemsTheSame(oldItem: RecyclerEntity, newItem: RecyclerEntity): Boolean =
            oldItem.recyclerId == newItem.recyclerId

        override fun areContentsTheSame(oldItem: RecyclerEntity, newItem: RecyclerEntity): Boolean =
            oldItem == newItem
    }
}
