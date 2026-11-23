package com.exambrowser.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ItemUjian(
    val mapel: String,
    val url: String,
    val token: String
)

class MainActivity : AppCompatActivity() {

    private val SHEET_API_URL = "https://script.google.com/macros/s/AKfycbwaTTgYWZPEczW6o1p_Zfg0uRr6QgEw_p1sVuS5JUyD7FZSizWZGU-hs6BX2MRix8JSWg/exec"

    private lateinit var etUrl: EditText
    private lateinit var containerDaftarUjian: LinearLayout
    private lateinit var tvStatusJadwal: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etUrl = findViewById(R.id.etUrl)
        containerDaftarUjian = findViewById(R.id.containerDaftarUjian)
        tvStatusJadwal = findViewById(R.id.tvStatusJadwal)

        val btnRefreshJadwal: Button = findViewById(R.id.btnRefreshJadwal)
        val btnMulai: Button = findViewById(R.id.btnMulai)
        val btnScanQr: Button = findViewById(R.id.btnScanQr)
        val tvCopyright: TextView = findViewById(R.id.tvCopyright)

        val prefs = getSharedPreferences("exam_prefs", MODE_PRIVATE)
        etUrl.setText(prefs.getString("last_url", ""))

        muatDaftarUjian()

        btnRefreshJadwal.setOnClickListener {
            muatDaftarUjian()
        }

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

        tvCopyright.setOnClickListener {
            try {
                val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/6285647018388"))
                startActivity(waIntent)
            } catch (_: Exception) {
                Toast.makeText(this, "Tidak dapat membuka WhatsApp", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun muatDaftarUjian() {
        tvStatusJadwal.text = "Mengambil daftar ujian..."
        containerDaftarUjian.removeAllViews()

        Thread {
            try {
                val url = URL(SHEET_API_URL)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(text)
                val arr = root.optJSONArray("ujian")
                val list = mutableListOf<ItemUjian>()

                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val mapel = obj.optString("mapel", "").trim()
                        val examUrl = obj.optString("url", "").trim()
                        val token = obj.optString("token", "").trim()
                        if (mapel.isNotEmpty() && examUrl.isNotEmpty()) {
                            list.add(ItemUjian(mapel, examUrl, token))
                        }
                    }
                }

                runOnUiThread {
                    if (list.isEmpty()) {
                        tvStatusJadwal.text = "Belum ada mata uji aktif di jadwal."
                    } else {
                        tvStatusJadwal.text = "Pilih mata uji lalu masukkan token:"
                        tampilkanTombolUjian(list)
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    tvStatusJadwal.text = "Gagal memuat jadwal. Gunakan Refresh atau input manual."
                }
            }
        }.start()
    }

    private fun tampilkanTombolUjian(list: List<ItemUjian>) {
        containerDaftarUjian.removeAllViews()
        val marginBawah = (8 * resources.displayMetrics.density).toInt()

        for (item in list) {
            val btn = Button(this).apply {
                text = item.mapel
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#0277BD"))
                isAllCaps = false
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = marginBawah
                }
                setOnClickListener {
                    if (item.token.isEmpty()) {
                        bukaUjian(item.url)
                    } else {
                        tampilkanDialogToken(item)
                    }
                }
            }
            containerDaftarUjian.addView(btn)
        }
    }

    private fun tampilkanDialogToken(item: ItemUjian) {
        val input = EditText(this).apply {
            hint = "Masukkan token ${item.mapel}"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setPadding(36, 28, 36, 28)
        }

        AlertDialog.Builder(this)
            .setTitle("Token Masuk: ${item.mapel}")
            .setMessage("Masukkan token ujian dari pengawas:")
            .setView(input)
            .setPositiveButton("MASUK") { _, _ ->
                val tokenInput = input.text.toString().trim()
                if (tokenInput.equals(item.token, ignoreCase = true)) {
                    var finalUrl = item.url
                    if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
                        finalUrl = "https://$finalUrl"
                    }
                    bukaUjian(finalUrl)
                } else {
                    Toast.makeText(this, "Token salah! Periksa kembali token dari pengawas.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("BATAL", null)
            .show()
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
