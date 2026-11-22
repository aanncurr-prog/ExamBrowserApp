package com.exambrowser.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

class MainActivity : AppCompatActivity() {

    private lateinit var etUrl: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etUrl = findViewById(R.id.etUrl)
        val btnMulai: Button = findViewById(R.id.btnMulai)
        val btnScanQr: Button = findViewById(R.id.btnScanQr)

        val prefs = getSharedPreferences("exam_prefs", MODE_PRIVATE)
        etUrl.setText(prefs.getString("last_url", ""))

        btnMulai.setOnClickListener {
            var url = etUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Masukkan URL ujian terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            prefs.edit().putString("last_url", url).apply()
            bukaUjian(url)
        }

        btnScanQr.setOnClickListener {
            mulaiScanQr()
        }
    }

    private fun mulaiScanQr() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()

        val scanner = GmsBarcodeScanning.getClient(this, options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val rawValue = barcode.rawValue
                if (!rawValue.isNullOrEmpty()) {
                    etUrl.setText(rawValue)
                    getSharedPreferences("exam_prefs", MODE_PRIVATE)
                        .edit().putString("last_url", rawValue).apply()
                    bukaUjian(rawValue)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal scan QR: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun bukaUjian(url: String) {
        val intent = Intent(this, ExamActivity::class.java)
        intent.putExtra("EXAM_URL", url)
        startActivity(intent)
    }
}
