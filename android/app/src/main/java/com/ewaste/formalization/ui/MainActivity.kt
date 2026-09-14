package com.ewaste.formalization.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ewaste.formalization.R
import com.ewaste.formalization.data.local.EWasteDatabase
import com.ewaste.formalization.worker.SyncManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var database: EWasteDatabase
    private lateinit var batchAdapter: BatchAdapter

    private lateinit var tvCollectorName: TextView
    private lateinit var tvCollectorId: TextView
    private lateinit var tvSyncIcon: TextView
    private lateinit var tvSyncMessage: TextView
    private lateinit var btnQuickSync: Button
    private lateinit var btnSyncStatusHeader: Button
    private lateinit var cardCaptureAction: LinearLayout
    private lateinit var cardHandoverAction: LinearLayout
    private lateinit var tvTotalWeight: TextView
    private lateinit var tvTotalBatches: TextView
    private lateinit var rvRecentBatches: RecyclerView
    private lateinit var tvEmptyBatches: TextView
    private lateinit var tvCollectorRatingHeader: TextView
    private lateinit var tvCollectorRating: TextView
    private lateinit var tvCollectorPoints: TextView
    private lateinit var tvCollectorTierBadge: TextView
    private lateinit var tvSuccessfulHandovers: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = EWasteDatabase.getInstance(this)

        // Schedule periodic sync via WorkManager in background
        SyncManager.schedulePeriodicSync(this)

        initViews()
        setupRecyclerView()
        observeData()
        setupListeners()
    }

    private fun initViews() {
        tvCollectorName = findViewById(R.id.tvCollectorName)
        tvCollectorId = findViewById(R.id.tvCollectorId)
        tvSyncIcon = findViewById(R.id.tvSyncIcon)
        tvSyncMessage = findViewById(R.id.tvSyncMessage)
        btnQuickSync = findViewById(R.id.btnQuickSync)
        btnSyncStatusHeader = findViewById(R.id.btnSyncStatusHeader)
        cardCaptureAction = findViewById(R.id.cardCaptureAction)
        cardHandoverAction = findViewById(R.id.cardHandoverAction)
        tvTotalWeight = findViewById(R.id.tvTotalWeight)
        tvTotalBatches = findViewById(R.id.tvTotalBatches)
        rvRecentBatches = findViewById(R.id.rvRecentBatches)
        tvEmptyBatches = findViewById(R.id.tvEmptyBatches)
        tvCollectorRatingHeader = findViewById(R.id.tvCollectorRatingHeader)
        tvCollectorRating = findViewById(R.id.tvCollectorRating)
        tvCollectorPoints = findViewById(R.id.tvCollectorPoints)
        tvCollectorTierBadge = findViewById(R.id.tvCollectorTierBadge)
        tvSuccessfulHandovers = findViewById(R.id.tvSuccessfulHandovers)
    }

    private fun setupRecyclerView() {
        batchAdapter = BatchAdapter { selectedBatch ->
            val intent = Intent(this, BatchSummaryActivity::class.java).apply {
                putExtra("BATCH_ID", selectedBatch.batchId)
            }
            startActivity(intent)
        }
        rvRecentBatches.layoutManager = LinearLayoutManager(this)
        rvRecentBatches.adapter = batchAdapter
    }

    private fun observeData() {
        // Observe Batches
        lifecycleScope.launch {
            database.batchDao().getAllBatchesFlow().collectLatest { batches ->
                batchAdapter.submitList(batches)
                tvTotalBatches.text = batches.size.toString()
                tvEmptyBatches.visibility = if (batches.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        // Observe Total Weight
        lifecycleScope.launch {
            database.batchDao().getTotalWeightCollectedFlow().collectLatest { weight ->
                val formatted = String.format("%.1f Kg", weight ?: 0.0)
                tvTotalWeight.text = formatted
            }
        }

        // Observe Unsynced Count
        lifecycleScope.launch {
            database.batchDao().getUnsyncedCountFlow().collectLatest { unsyncedCount ->
                if (unsyncedCount == 0) {
                    tvSyncIcon.text = "✅"
                    tvSyncMessage.text = getString(R.string.sync_all_safe)
                    btnQuickSync.visibility = View.GONE
                } else {
                    tvSyncIcon.text = "⏳"
                    tvSyncMessage.text = getString(R.string.sync_offline_pending, unsyncedCount)
                    btnQuickSync.visibility = View.VISIBLE
                }
            }
        }

        // Observe Active Collector
        lifecycleScope.launch {
            database.collectorDao().getActiveCollectorFlow().collectLatest { collector ->
                collector?.let {
                    tvCollectorName.text = it.fullName
                    tvCollectorId.text = "ID: ${it.collectorId} • ${it.operatingTerritory}"

                    val formattedRating = String.format(java.util.Locale.US, "%.1f", it.rating)
                    val tierHindi = when (it.incentiveTier.uppercase()) {
                        "GOLD" -> "गोल्ड"
                        "SILVER" -> "सिल्वर"
                        else -> "ब्रॉन्ज़"
                    }
                    tvCollectorRatingHeader.text = "⭐ $formattedRating • $tierHindi कलेक्टर"
                    tvCollectorRating.text = "⭐ $formattedRating / 5.0"
                    tvCollectorPoints.text = "🎁 ${it.incentivePoints} Pts"
                    tvCollectorTierBadge.text = it.incentiveTier.uppercase()
                    tvSuccessfulHandovers.text = "सफल हैंडओवर: ${it.successfulHandovers} बार"
                }
            }
        }
    }

    private fun setupListeners() {
        // Big Green Action: Capture / Scan E-Waste
        cardCaptureAction.setOnClickListener {
            startActivity(Intent(this, CaptureItemActivity::class.java))
        }

        // Big Blue Action: Handover to Recycler
        cardHandoverAction.setOnClickListener {
            startActivity(Intent(this, HandoverActivity::class.java))
        }

        // Quick Sync
        btnQuickSync.setOnClickListener {
            SyncManager.triggerImmediateSync(this)
            Toast.makeText(this, "क्लाउड अपलोड शुरू हो रहा है... (Syncing)", Toast.LENGTH_SHORT).show()
        }

        // Header Sync Status
        btnSyncStatusHeader.setOnClickListener {
            startActivity(Intent(this, SyncStatusActivity::class.java))
        }
    }
}
