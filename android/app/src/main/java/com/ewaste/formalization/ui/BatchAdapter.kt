package com.ewaste.formalization.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ewaste.formalization.R
import com.ewaste.formalization.data.local.entity.BatchEntity
import com.ewaste.formalization.data.local.entity.HandoverStatus
import com.ewaste.formalization.data.local.entity.SyncStatus
import java.text.SimpleDateFormat
import java.util.*

class BatchAdapter(
    private val onBatchClicked: (BatchEntity) -> Unit
) : ListAdapter<BatchEntity, BatchAdapter.BatchViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BatchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_batch_card, parent, false)
        return BatchViewHolder(view, onBatchClicked)
    }

    override fun onBindViewHolder(holder: BatchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class BatchViewHolder(
        itemView: View,
        private val onBatchClicked: (BatchEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvItemIcon: TextView = itemView.findViewById(R.id.tvItemIcon)
        private val tvCategoryTitle: TextView = itemView.findViewById(R.id.tvCategoryTitle)
        private val tvCpcbCode: TextView = itemView.findViewById(R.id.tvCpcbCode)
        private val tvSyncBadge: TextView = itemView.findViewById(R.id.tvSyncBadge)
        private val tvWeightAndCount: TextView = itemView.findViewById(R.id.tvWeightAndCount)
        private val tvHandoverStatusBadge: TextView = itemView.findViewById(R.id.tvHandoverStatusBadge)
        private val tvPassportBatchId: TextView = itemView.findViewById(R.id.tvPassportBatchId)

        fun bind(batch: BatchEntity) {
            tvItemIcon.text = getCategoryIcon(batch.cpcbCategoryCode)
            tvCategoryTitle.text = batch.itemCategory
            tvCpcbCode.text = "CPCB: ${batch.cpcbCategoryCode} • AI: ${(batch.aiConfidence * 100).toInt()}%"
            tvWeightAndCount.text = "⚖️ ${batch.estimatedWeightKg} Kg • ${batch.itemCount} Units"

            val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
            val dateStr = dateFormat.format(Date(batch.timestamp))
            val shortId = if (batch.batchId.length > 8) batch.batchId.take(8) else batch.batchId
            tvPassportBatchId.text = "Passport: #$shortId • $dateStr"

            // Sync badge
            when (batch.syncStatus) {
                SyncStatus.SYNCED -> {
                    tvSyncBadge.text = "☁️ SYNCED"
                    tvSyncBadge.setBackgroundColor(0xFFE8F5E9.toInt())
                    tvSyncBadge.setTextColor(0xFF1E7E34.toInt())
                }
                SyncStatus.PENDING_SYNC, SyncStatus.LOCAL_ONLY -> {
                    tvSyncBadge.text = "⏳ OFFLINE"
                    tvSyncBadge.setBackgroundColor(0xFFFFF3CD.toInt())
                    tvSyncBadge.setTextColor(0xFF856404.toInt())
                }
                SyncStatus.SYNC_FAILED -> {
                    tvSyncBadge.text = "⚠️ RETRY"
                    tvSyncBadge.setBackgroundColor(0xFFF8D7DA.toInt())
                    tvSyncBadge.setTextColor(0xFF721C24.toInt())
                }
            }

            // Handover Status badge
            when (batch.handoverStatus) {
                HandoverStatus.COLLECTED -> {
                    tvHandoverStatusBadge.text = "📦 COLLECTED"
                    tvHandoverStatusBadge.setBackgroundColor(0xFFE2E3E5.toInt())
                    tvHandoverStatusBadge.setTextColor(0xFF383D41.toInt())
                }
                HandoverStatus.IN_TRANSIT -> {
                    tvHandoverStatusBadge.text = "🚚 IN-TRANSIT"
                    tvHandoverStatusBadge.setBackgroundColor(0xFFCCE5FF.toInt())
                    tvHandoverStatusBadge.setTextColor(0xFF004085.toInt())
                }
                HandoverStatus.HANDED_OVER -> {
                    tvHandoverStatusBadge.text = "🤝 HANDED OVER"
                    tvHandoverStatusBadge.setBackgroundColor(0xFFD1ECF1.toInt())
                    tvHandoverStatusBadge.setTextColor(0xFF0C5460.toInt())
                }
                HandoverStatus.VERIFIED_BY_RECYCLER -> {
                    tvHandoverStatusBadge.text = "✅ EPR VERIFIED"
                    tvHandoverStatusBadge.setBackgroundColor(0xFFD4EDDA.toInt())
                    tvHandoverStatusBadge.setTextColor(0xFF155724.toInt())
                }
            }

            itemView.setOnClickListener { onBatchClicked(batch) }
        }

        private fun getCategoryIcon(code: String): String = when (code) {
            "ITEW15" -> "📱"
            "ITEW3" -> "💻"
            "ITEW_PCB" -> "🧩"
            "CEEW1" -> "🖥️"
            "BATT_LII" -> "🔋"
            "CBL_COP" -> "🔌"
            "ITEW4" -> "🖨️"
            else -> "⚡"
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<BatchEntity>() {
        override fun areItemsTheSame(oldItem: BatchEntity, newItem: BatchEntity): Boolean =
            oldItem.batchId == newItem.batchId

        override fun areContentsTheSame(oldItem: BatchEntity, newItem: BatchEntity): Boolean =
            oldItem == newItem
    }
}
