package com.ewaste.formalization.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ewaste.formalization.R
import com.ewaste.formalization.data.local.EWasteDatabase
import com.ewaste.formalization.data.local.entity.BatchEntity
import com.ewaste.formalization.data.local.entity.HandoverStatus
import com.ewaste.formalization.data.local.entity.SyncStatus
import com.ewaste.formalization.ml.ClassificationResult
import com.ewaste.formalization.ml.EWasteClassifier
import com.ewaste.formalization.worker.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*

class CaptureItemActivity : AppCompatActivity() {

    private lateinit var database: EWasteDatabase
    private lateinit var classifier: EWasteClassifier

    private lateinit var ivCapturedPhoto: ImageView
    private lateinit var layoutCameraPrompt: View
    private lateinit var btnCapturePhoto: Button
    private lateinit var btnRetakePhoto: Button
    private lateinit var cardAiResult: View
    private lateinit var tvAiIcon: TextView
    private lateinit var tvAiCategoryName: TextView
    private lateinit var tvAiCpcbCode: TextView
    private lateinit var tvAiConfidence: TextView
    private lateinit var tvManualOverrideIndicator: TextView
    private lateinit var etWeightKg: EditText
    private lateinit var etItemCount: EditText
    private lateinit var btnSavePassport: Button

    // Current State
    private var currentBitmap: Bitmap? = null
    private var selectedCategoryKey: String = "mobile_phone"
    private var selectedCategoryDisplay: String = "Mobile Phones (मोबाइल फोन)"
    private var selectedCpcbCode: String = "ITEW15"
    private var aiConfidenceScore: Float = 0.92f
    private var isManualOverride: Boolean = false
    private var savedImageFilePath: String? = null

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            handleCapturedPhoto(bitmap)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_capture_item)

        database = EWasteDatabase.getInstance(this)
        classifier = EWasteClassifier(this)

        initViews()
        setupCategoryOverrideButtons()
        setupWeightPresetButtons()
        setupListeners()

        // Auto-initialize with a sample e-waste item so kabadiwala gets instant feedback
        val initialSample = createSimulatedEWasteBitmap("mobile_phone")
        handleCapturedPhoto(initialSample)
    }

    private fun initViews() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        ivCapturedPhoto = findViewById(R.id.ivCapturedPhoto)
        layoutCameraPrompt = findViewById(R.id.layoutCameraPrompt)
        btnCapturePhoto = findViewById(R.id.btnCapturePhoto)
        btnRetakePhoto = findViewById(R.id.btnRetakePhoto)
        cardAiResult = findViewById(R.id.cardAiResult)
        tvAiIcon = findViewById(R.id.tvAiIcon)
        tvAiCategoryName = findViewById(R.id.tvAiCategoryName)
        tvAiCpcbCode = findViewById(R.id.tvAiCpcbCode)
        tvAiConfidence = findViewById(R.id.tvAiConfidence)
        tvManualOverrideIndicator = findViewById(R.id.tvManualOverrideIndicator)
        etWeightKg = findViewById(R.id.etWeightKg)
        etItemCount = findViewById(R.id.etItemCount)
        btnSavePassport = findViewById(R.id.btnSavePassport)
    }

    private fun setupListeners() {
        btnCapturePhoto.setOnClickListener { launchCameraIntent() }
        btnRetakePhoto.setOnClickListener { launchCameraIntent() }

        btnSavePassport.setOnClickListener {
            saveDigitalMaterialPassport()
        }
    }

    private fun launchCameraIntent() {
        try {
            cameraLauncher.launch(null)
        } catch (e: Exception) {
            e.printStackTrace()
            // If camera app not found in emulator, cycle simulated e-waste samples
            val sampleKeys = listOf("mobile_phone", "pcb_circuit_board", "battery_pack", "copper_cable_wire", "crt_lcd_monitor")
            val nextKey = sampleKeys[(sampleKeys.indexOf(selectedCategoryKey) + 1) % sampleKeys.size]
            handleCapturedPhoto(createSimulatedEWasteBitmap(nextKey))
            Toast.makeText(this, "कैमरा सिमुलेशन: $nextKey", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCapturedPhoto(bitmap: Bitmap) {
        currentBitmap = bitmap
        ivCapturedPhoto.setImageBitmap(bitmap)
        layoutCameraPrompt.visibility = View.GONE
        btnRetakePhoto.visibility = View.VISIBLE

        // Run On-Device TFLite inference
        lifecycleScope.launch(Dispatchers.Default) {
            val results = classifier.classify(bitmap)
            if (results.isNotEmpty()) {
                val top = results[0]
                withContext(Dispatchers.Main) {
                    applyClassificationResult(top, isManual = false)
                }
            }
        }
    }

    private fun applyClassificationResult(result: ClassificationResult, isManual: Boolean) {
        selectedCategoryKey = result.category
        selectedCategoryDisplay = result.displayName
        selectedCpcbCode = result.cpcbCode
        aiConfidenceScore = if (isManual) 1.0f else result.confidence
        isManualOverride = isManual

        tvAiCategoryName.text = result.displayName
        tvAiCpcbCode.text = "CPCB Schedule I: ${result.cpcbCode}"
        tvAiConfidence.text = "${(aiConfidenceScore * 100).toInt()}% विश्वास"
        tvAiIcon.text = getCategoryIcon(result.cpcbCode)

        tvManualOverrideIndicator.visibility = if (isManual) View.VISIBLE else View.GONE
    }

    private fun setupCategoryOverrideButtons() {
        val map = mapOf(
            R.id.btnCatPhone to Triple("mobile_phone", "ITEW15", "Mobile Phones / Smartphones (मोबाइल फोन)"),
            R.id.btnCatLaptop to Triple("laptop_computer", "ITEW3", "Laptops & Computers (लैपटॉप)"),
            R.id.btnCatPcb to Triple("pcb_circuit_board", "ITEW_PCB", "Circuit Boards / PCB (सर्किट बोर्ड)"),
            R.id.btnCatMonitor to Triple("crt_lcd_monitor", "CEEW1", "TV / CRT / LCD Monitors (टीवी मॉनिटर)"),
            R.id.btnCatBattery to Triple("battery_pack", "BATT_LII", "Lithium / Secondary Batteries (बैटरी)"),
            R.id.btnCatCable to Triple("copper_cable_wire", "CBL_COP", "Copper Cables & Wires (तांबे के तार)"),
            R.id.btnCatPrinter to Triple("printer_peripheral", "ITEW4", "Printers & Peripherals (प्रिंटर)"),
            R.id.btnCatAppliance to Triple("household_appliance_small", "CEEW_SHA", "Small Appliances (छोटे उपकरण)")
        )

        for ((btnId, info) in map) {
            findViewById<Button>(btnId).setOnClickListener {
                val result = ClassificationResult(
                    category = info.first,
                    displayName = info.third,
                    cpcbCode = info.second,
                    confidence = 1.0f
                )
                applyClassificationResult(result, isManual = true)
                Toast.makeText(this, "श्रेणी चुनी गई: ${info.third}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupWeightPresetButtons() {
        val btn1 = findViewById<Button>(R.id.btnWeight1kg)
        val btn5 = findViewById<Button>(R.id.btnWeight5kg)
        val btn10 = findViewById<Button>(R.id.btnWeight10kg)
        val btn25 = findViewById<Button>(R.id.btnWeight25kg)

        val buttons = listOf(btn1, btn5, btn10, btn25)

        fun selectWeight(weight: Double, activeBtn: Button) {
            etWeightKg.setText(weight.toString())
            buttons.forEach {
                it.setBackgroundColor(if (it == activeBtn) 0xFFD4EDDA.toInt() else 0xFFFFFFFF.toInt())
            }
        }

        btn1.setOnClickListener { selectWeight(1.0, btn1) }
        btn5.setOnClickListener { selectWeight(5.0, btn5) }
        btn10.setOnClickListener { selectWeight(10.0, btn10) }
        btn25.setOnClickListener { selectWeight(25.0, btn25) }
    }

    private fun saveDigitalMaterialPassport() {
        val weightText = etWeightKg.text.toString().trim()
        val countText = etItemCount.text.toString().trim()

        val weight = weightText.toDoubleOrNull() ?: 1.0
        val count = countText.toIntOrNull() ?: 1

        val batchId = "BATCH-" + UUID.randomUUID().toString().take(12).uppercase()
        val qrPasscode = (100000 + Random().nextInt(900000)).toString()

        // Cache image locally
        currentBitmap?.let { bmp ->
            try {
                val file = File(filesDir, "$batchId.jpg")
                val fos = FileOutputStream(file)
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                fos.flush()
                fos.close()
                savedImageFilePath = file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val collector = database.collectorDao().getActiveCollector()
            val collectorId = collector?.collectorId ?: "KAB-DL-2024-001"

            // Delhi / Seelampur collection GPS coordinates
            val latitude = 28.6692 + (Random().nextDouble() - 0.5) * 0.01
            val longitude = 77.2764 + (Random().nextDouble() - 0.5) * 0.01

            val newBatch = BatchEntity(
                batchId = batchId,
                collectorId = collectorId,
                itemCategory = selectedCategoryDisplay,
                cpcbCategoryCode = selectedCpcbCode,
                aiConfidence = aiConfidenceScore,
                manualOverride = isManualOverride,
                estimatedWeightKg = weight,
                itemCount = count,
                latitude = latitude,
                longitude = longitude,
                locationAddress = "Seelampur Ward 4, East Delhi",
                timestamp = System.currentTimeMillis(),
                handoverStatus = HandoverStatus.COLLECTED,
                recyclerId = null,
                syncStatus = SyncStatus.LOCAL_ONLY,
                localImagePath = savedImageFilePath,
                qrPasscode = qrPasscode,
                recyclerVerificationHash = null
            )

            database.batchDao().insertBatch(newBatch)
            database.collectorDao().incrementCollectedWeight(collectorId, weight)

            // Trigger WorkManager sync immediately in background
            SyncManager.triggerImmediateSync(applicationContext)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@CaptureItemActivity, "पासपोर्ट सुरक्षित! (Saved Offline)", Toast.LENGTH_SHORT).show()
                val intent = Intent(this@CaptureItemActivity, BatchSummaryActivity::class.java).apply {
                    putExtra("BATCH_ID", batchId)
                }
                startActivity(intent)
                finish()
            }
        }
    }

    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun createSimulatedEWasteBitmap(categoryKey: String): Bitmap {
        val size = 400
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (categoryKey) {
            "pcb_circuit_board" -> {
                canvas.drawColor(Color.parseColor("#1B5E20")) // Deep PCB Green
                paint.color = Color.parseColor("#FFD54F")
                paint.strokeWidth = 6f
                for (i in 40 until size step 40) {
                    canvas.drawLine(i.toFloat(), 20f, i.toFloat(), size - 20f, paint)
                    canvas.drawLine(20f, i.toFloat(), size - 20f, i.toFloat(), paint)
                }
            }
            "copper_cable_wire" -> {
                canvas.drawColor(Color.parseColor("#3E2723"))
                paint.color = Color.parseColor("#FF6D00") // Copper orange
                paint.strokeWidth = 14f
                canvas.drawLine(40f, 60f, size - 40f, size - 60f, paint)
                canvas.drawLine(60f, 300f, size - 60f, 100f, paint)
            }
            "battery_pack" -> {
                canvas.drawColor(Color.parseColor("#212121"))
                paint.color = Color.parseColor("#29B6F6") // Blue Li-ion wrap
                canvas.drawRect(80f, 100f, size - 80f, size - 100f, paint)
            }
            else -> {
                // Mobile phone (dark rectangle screen)
                canvas.drawColor(Color.parseColor("#263238"))
                paint.color = Color.parseColor("#455A64")
                canvas.drawRoundRect(100f, 40f, size - 100f, size - 40f, 24f, 24f, paint)
                paint.color = Color.parseColor("#102027")
                canvas.drawRect(115f, 70f, size - 115f, size - 80f, paint)
            }
        }
        return bitmap
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

    override fun onDestroy() {
        super.onDestroy()
        classifier.close()
    }
}
