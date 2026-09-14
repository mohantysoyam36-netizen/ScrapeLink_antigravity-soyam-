package com.ewaste.formalization.ui

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ewaste.formalization.R
import com.ewaste.formalization.data.local.EWasteDatabase
import com.ewaste.formalization.data.local.entity.BatchEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class BatchSummaryActivity : AppCompatActivity() {

    private lateinit var database: EWasteDatabase
    private var currentBatchId: String? = null
    private var currentBatch: BatchEntity? = null

    private lateinit var ivQrCode: ImageView
    private lateinit var tvQrPasscode: TextView
    private lateinit var tvDetailBatchId: TextView
    private lateinit var tvDetailCategory: TextView
    private lateinit var tvDetailWeight: TextView
    private lateinit var tvDetailLocation: TextView
    private lateinit var tvDetailStatus: TextView
    private lateinit var btnProceedHandover: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_batch_summary)

        database = EWasteDatabase.getInstance(this)
        currentBatchId = intent.getStringExtra("BATCH_ID")

        initViews()
        loadBatchData()
    }

    private fun initViews() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        ivQrCode = findViewById(R.id.ivQrCode)
        tvQrPasscode = findViewById(R.id.tvQrPasscode)
        tvDetailBatchId = findViewById(R.id.tvDetailBatchId)
        tvDetailCategory = findViewById(R.id.tvDetailCategory)
        tvDetailWeight = findViewById(R.id.tvDetailWeight)
        tvDetailLocation = findViewById(R.id.tvDetailLocation)
        tvDetailStatus = findViewById(R.id.tvDetailStatus)
        btnProceedHandover = findViewById(R.id.btnProceedHandover)

        btnProceedHandover.setOnClickListener {
            val intent = Intent(this, HandoverActivity::class.java).apply {
                putExtra("BATCH_ID", currentBatchId)
            }
            startActivity(intent)
        }
    }

    private fun loadBatchData() {
        val id = currentBatchId ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val batch = database.batchDao().getBatchById(id)
            withContext(Dispatchers.Main) {
                if (batch != null) {
                    currentBatch = batch
                    populateViews(batch)
                } else {
                    Toast.makeText(this@BatchSummaryActivity, "बैच नहीं मिला (Batch not found)", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun populateViews(batch: BatchEntity) {
        tvDetailBatchId.text = "Batch ID: ${batch.batchId}"
        tvDetailCategory.text = "Category: ${batch.itemCategory} (${batch.cpcbCategoryCode})"
        tvDetailWeight.text = "Weight: ${batch.estimatedWeightKg} Kg • ${batch.itemCount} Units"

        val latFormatted = String.format(Locale.US, "%.4f", batch.latitude)
        val lonFormatted = String.format(Locale.US, "%.4f", batch.longitude)
        val locationText = batch.locationAddress ?: "GPS: $latFormatted, $lonFormatted"
        tvDetailLocation.text = "Location: $locationText ($latFormatted, $lonFormatted)"

        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val dateFormatted = dateFormat.format(Date(batch.timestamp))
        tvDetailStatus.text = "Status: ${batch.handoverStatus.name} • Sync: ${batch.syncStatus.name} • $dateFormatted"

        tvQrPasscode.text = "Passcode: ${batch.qrPasscode}"

        // Generate Traceable QR Code Payload
        val qrPayload = """
            {"batchId":"${batch.batchId}","collectorId":"${batch.collectorId}","cpcb":"${batch.cpcbCategoryCode}","weightKg":${batch.estimatedWeightKg},"units":${batch.itemCount},"passcode":"${batch.qrPasscode}"}
        """.trimIndent()

        val qrBitmap = QrGenerator.generateQrBitmap(qrPayload, 400)
        ivQrCode.setImageBitmap(qrBitmap)

        if (batch.handoverStatus.name == "VERIFIED_BY_RECYCLER") {
            btnProceedHandover.isEnabled = false
            btnProceedHandover.text = "✅ रीसाइक्लर द्वारा सत्यापित (Verified by Recycler)"
        }
    }
}
