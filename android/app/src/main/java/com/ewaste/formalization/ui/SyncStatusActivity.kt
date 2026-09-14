package com.ewaste.formalization.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ewaste.formalization.R
import com.ewaste.formalization.data.local.EWasteDatabase
import com.ewaste.formalization.data.remote.NetworkClient
import com.ewaste.formalization.worker.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SyncStatusActivity : AppCompatActivity() {

    private lateinit var database: EWasteDatabase
    private lateinit var batchAdapter: BatchAdapter

    private lateinit var tvSyncOverviewStatus: TextView
    private lateinit var btnTriggerManualSync: Button
    private lateinit var etBaseUrl: EditText
    private lateinit var btnSaveUrl: Button
    private lateinit var rvPendingSyncBatches: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync_status)

        database = EWasteDatabase.getInstance(this)

        initViews()
        setupRecyclerView()
        observeData()
    }

    private fun initViews() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        tvSyncOverviewStatus = findViewById(R.id.tvSyncOverviewStatus)
        btnTriggerManualSync = findViewById(R.id.btnTriggerManualSync)
        etBaseUrl = findViewById(R.id.etBaseUrl)
        btnSaveUrl = findViewById(R.id.btnSaveUrl)
        rvPendingSyncBatches = findViewById(R.id.rvPendingSyncBatches)

        etBaseUrl.setText(NetworkClient.getBaseUrl())

        btnSaveUrl.setOnClickListener {
            val url = etBaseUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                NetworkClient.updateBaseUrl(url)
                Toast.makeText(this, "सर्वर URL अपडेट हुआ: $url", Toast.LENGTH_SHORT).show()
            }
        }

        btnTriggerManualSync.setOnClickListener {
            SyncManager.triggerImmediateSync(this)
            Toast.makeText(this, "अपलोड शुरू किया गया... (Uploading)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        batchAdapter = BatchAdapter { }
        rvPendingSyncBatches.layoutManager = LinearLayoutManager(this)
        rvPendingSyncBatches.adapter = batchAdapter
    }

    private fun observeData() {
        lifecycleScope.launch {
            database.batchDao().getUnsyncedCountFlow().collectLatest { count ->
                if (count == 0) {
                    tvSyncOverviewStatus.text = "✅ सभी पासपोर्ट रिकॉर्ड सर्वर पर सिंक हैं"
                    tvSyncOverviewStatus.setTextColor(0xFF1E7E34.toInt())
                } else {
                    tvSyncOverviewStatus.text = "⏳ $count बैच स्थानीय फोन में सुरक्षित (Unsynced)"
                    tvSyncOverviewStatus.setTextColor(0xFFD39E00.toInt())
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val unsyncedList = database.batchDao().getUnsyncedBatches()
            withContext(Dispatchers.Main) {
                batchAdapter.submitList(unsyncedList)
            }
        }
    }
}
