package com.exambrowser.app

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ExamActivity : AppCompatActivity() {

    private val SHEET_API_URL = "https://script.google.com/macros/s/AKfycbwaTTgYWZPEczW6o1p_Zfg0uRr6QgEw_p1sVuS5JUyD7FZSizWZGU-hs6BX2MRix8JSWg/exec"

    private lateinit var webView: WebView
    private var sedangKeluarAtauScan = false
    private var alarmAktif = false
    private var toneGenerator: ToneGenerator? = null
    private val alarmHandler = Handler(Looper.getMainLooper())

    private val alarmRunnable = object : Runnable {
        override fun run() {
            if (alarmAktif) {
                try {
                    if (toneGenerator == null) {
                        toneGenerator = ToneGenerator(
                            ToneGenerator.MAX_VOLUME.let {
                                android.media.AudioManager.STREAM_ALARM
                            },
                            ToneGenerator.MAX_VOLUME
                        )
                    }
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700)
                } catch (_: Exception) {}
                alarmHandler.postDelayed(this, 1000)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Blokir screenshot & screen record
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // Jaga layar tetap menyala selama ujian
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_exam)
        sembunyikanSystemBars()

        // Aktifkan Lock Task Mode (Screen Pinning) jika didukung
        try {
            startLockTask()
        } catch (_: Exception) {}

        webView = findViewById(R.id.webView)
        val btnKeluar: Button = findViewById(R.id.btnKeluar)

        val examUrl = intent.getStringExtra("EXAM_URL").orEmpty().trim()
        if (examUrl.isEmpty()) {
            Toast.makeText(this, "URL ujian tidak valid", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupWebView()
        webView.loadUrl(examUrl)

        btnKeluar.setOnClickListener {
            mulaiScanKeluar()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString = "$userAgentString ExamBrowserSMKN1Trucuk/1.0"
        }

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return false
            }
        }
    }

    private fun sembunyikanSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }

    private fun mulaiScanKeluar() {
        sedangKeluarAtauScan = true
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()

        val scanner = GmsBarcodeScanning.getClient(this, options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                sedangKeluarAtauScan = false
                val scannedCode = barcode.rawValue?.trim().orEmpty()
                if (scannedCode.isEmpty()) {
                    Toast.makeText(this, "QR Code kosong", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                verifikasiKodeKeluar(scannedCode)
            }
            .addOnFailureListener { e ->
                sedangKeluarAtauScan = false
                Toast.makeText(this, "Gagal scan QR keluar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun verifikasiKodeKeluar(scannedCode: String) {
        Toast.makeText(this, "Memeriksa kode keluar...", Toast.LENGTH_SHORT).show()
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
                val keluarObj = root.optJSONObject("keluar")
                val aktif = keluarObj?.optBoolean("aktif", false) ?: false
                val kodeBenar = keluarObj?.optString("kode", "")?.trim().orEmpty()

                runOnUiThread {
                    if (aktif && kodeBenar.isNotEmpty() && scannedCode == kodeBenar) {
                        sedangKeluarAtauScan = true
                        hentikanAlarmJikaAda()
                        try {
                            stopLockTask()
                        } catch (_: Exception) {}
                        finish()
                    } else {
                        Toast.makeText(
                            this,
                            "QR Code keluar tidak valid atau tidak aktif",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Gagal cek kode keluar (periksa koneksi internet)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    private fun picuAlarmPelanggaran(pesan: String) {
        Toast.makeText(this, pesan, Toast.LENGTH_LONG).show()
        if (!alarmAktif) {
            alarmAktif = true
            alarmHandler.removeCallbacks(alarmRunnable)
            alarmHandler.post(alarmRunnable)
        }
    }

    private fun hentikanAlarmJikaAda() {
        alarmAktif = false
        alarmHandler.removeCallbacks(alarmRunnable)
        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        } catch (_: Exception) {}
        toneGenerator = null
    }

    // Hanya kembalikan mode layar penuh saat fokus kembali; JANGAN picu alarm saat mengetik/autofill
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            sembunyikanSystemBars()
        }
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (isInMultiWindowMode && !sedangKeluarAtauScan) {
            picuAlarmPelanggaran("Mode split-screen / multi-window terdeteksi!")
        }
    }

    override fun onPause() {
        super.onPause()
        if (!sedangKeluarAtauScan) {
            picuAlarmPelanggaran("Aplikasi keluar atau kehilangan fokus saat ujian!")
        }
    }

    override fun onResume() {
        super.onResume()
        sembunyikanSystemBars()
    }

    override fun onDestroy() {
        hentikanAlarmJikaAda()
        super.onDestroy()
    }
}
