package com.ewaste.formalization.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ewaste.formalization.R
import com.ewaste.formalization.data.local.EWasteDatabase
import com.ewaste.formalization.data.local.entity.HandoverEventEntity
import com.ewaste.formalization.data.local.entity.HandoverStatus
import com.ewaste.formalization.data.local.entity.RecyclerEntity
import com.ewaste.formalization.worker.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class HandoverActivity : AppCompatActivity() {

    private lateinit var database: EWasteDatabase
    private lateinit var recyclerAdapter: RecyclerAdapter

    private var targetBatchId: String? = null
    private var selectedRecycler: RecyclerEntity? = null

    private lateinit var tvHandoverBatchTitle: TextView
    private lateinit var tvHandoverBatchId: TextView
    private lateinit var rvRecyclers: RecyclerView
    private lateinit var btnConfirmHandover: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_handover)

        database = EWasteDatabase.getInstance(this)
        targetBatchId = intent.getStringExtra("BATCH_ID")

        initViews()
        setupRecyclerView()
        loadData()
    }

    private fun initViews() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        tvHandoverBatchTitle = findViewById(R.id.tvHandoverBatchTitle)
        tvHandoverBatchId = findViewById(R.id.tvHandoverBatchId)
        rvRecyclers = findViewById(R.id.rvRecyclers)
        btnConfirmHandover = findViewById(R.id.btnConfirmHandover)

        btnConfirmHandover.setOnClickListener {
            executeHandover()
        }
    }

    private fun setupRecyclerView() {
        recyclerAdapter = RecyclerAdapter { recycler ->
            selectedRecycler = recycler
        }
        rvRecyclers.layoutManager = LinearLayoutManager(this)
        rvRecyclers.adapter = recyclerAdapter
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Load recyclers
            var recyclers = database.recyclerDao().getAllRecyclers()
            if (recyclers.isEmpty()) {
                EWasteDatabase.seedInitialData(database)
                recyclers = database.recyclerDao().getAllRecyclers()
            }

            // Load batch (if not passed, pick the latest COLLECTED batch)
            val batch = if (targetBatchId != null) {
                database.batchDao().getBatchById(targetBatchId!!)
            } else {
                val allBatches = database.batchDao().getAllBatches()
                allBatches.firstOrNull { it.handoverStatus == HandoverStatus.COLLECTED }
            }

            withContext(Dispatchers.Main) {
                recyclerAdapter.submitList(recyclers)
                if (recyclers.isNotEmpty()) {
                    selectedRecycler = recyclers[0]
                }

                if (batch != null) {
                    targetBatchId = batch.batchId
                    tvHandoverBatchTitle.text = "बैच: ${batch.itemCategory} (${batch.estimatedWeightKg} Kg)"
                    tvHandoverBatchId.text = "Batch ID: ${batch.batchId}"
                } else {
                    tvHandoverBatchTitle.text = "कोई अप्रदत्त बैच नहीं है (No pending batches)"
                    tvHandoverBatchId.text = "पहले नया माल स्कैन करें"
                    btnConfirmHandover.isEnabled = false
                }
            }
        }
    }

    private fun executeHandover() {
        val batchId = targetBatchId ?: return
        val recycler = selectedRecycler ?: run {
            Toast.makeText(this, "कृपया अधिकृत रीसाइक्लर चुनें", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val batch = database.batchDao().getBatchById(batchId)
            if (batch == null) return@launch

            // Update batch in Room
            database.batchDao().markHandedOver(
                batchId = batchId,
                status = HandoverStatus.HANDED_OVER,
                recyclerId = recycler.recyclerId
            )

            // Audit Trail Handover Event
            val event = HandoverEventEntity(
                eventId = "EVT-" + UUID.randomUUID().toString().take(8).uppercase(),
                batchId = batchId,
                collectorId = batch.collectorId,
                recyclerId = recycler.recyclerId,
                previousStatus = batch.handoverStatus,
                newStatus = HandoverStatus.HANDED_OVER,
                eventTimestamp = System.currentTimeMillis(),
                latitude = batch.latitude,
                longitude = batch.longitude,
                verificationNotes = "Custody transferred to ${recycler.companyName} at collection depot"
            )
            database.handoverEventDao().insertEvent(event)

            // Award Collector Rating Increment & Incentive Points
            val pointsEarned = 50 + (batch.estimatedWeightKg * 10).toInt()
            database.collectorDao().recordSuccessfulHandover(
                collectorId = batch.collectorId,
                ratingDelta = 0.1,
                points = pointsEarned
            )
            val updatedCollector = database.collectorDao().getCollectorById(batch.collectorId)
            val newRating = updatedCollector?.rating ?: 4.6

            // Trigger WorkManager sync to push Handed Over state to cloud
            SyncManager.triggerImmediateSync(applicationContext)

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@HandoverActivity,
                    "🎉 माल सफलतापूर्वक सौंपा गया!\nरेटिंग बढ़कर ${String.format(Locale.US, "%.1f", newRating)}★ हुई (+${pointsEarned} प्रोत्साहन अंक मिले)",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }
}
