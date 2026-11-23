package com.exambrowser.app

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
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
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
    private val mainHandler = Handler(Looper.getMainLooper())

    private val alarmRunnable = object : Runnable {
        override fun run() {
            if (alarmAktif && !isFinishing) {
                try {
                    if (toneGenerator == null) {
                        toneGenerator = ToneGenerator(
                            AudioManager.STREAM_ALARM,
                            ToneGenerator.MAX_VOLUME
                        )
                    }
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700)
                } catch (_: Exception) {}
                mainHandler.postDelayed(this, 1000)
            }
        }
    }

    private val pendingPauseAlarmRunnable = Runnable {
        if (!sedangKeluarAtauScan && !isFinishing && !hasWindowFocus()) {
            picuAlarmPelanggaran("Aplikasi keluar atau berpindah layar saat ujian!")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hentikanAlarmJikaAda()

        // Blokir screenshot & screen record
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // Jaga layar tetap menyala selama ujian
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_exam)
        pasangListenerImmersive()
        sembunyikanSystemBars()
        blokirTombolBack()
        aktifkanLockTaskAman()

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
            tampilkanPilihanKeluar()
        }
    }

    private fun blokirTombolBack() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(
                    this@ExamActivity,
                    "Tombol Back dikunci selama ujian. Gunakan tombol Keluar.",
                    Toast.LENGTH_SHORT
                ).show()
                sembunyikanSystemBars()
            }
        })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Toast.makeText(
                this,
                "Tombol Back dikunci selama ujian. Gunakan tombol Keluar.",
                Toast.LENGTH_SHORT
            ).show()
            sembunyikanSystemBars()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun aktifkanLockTaskAman() {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val isLocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
            } else {
                @Suppress("DEPRECATION")
                am.isInLockTaskMode
            }
            if (!isLocked) {
                startLockTask()
            }
        } catch (_: Exception) {}
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
        }

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                sembunyikanSystemBars()
            }
        }
    }

    private fun pasangListenerImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.setOnApplyWindowInsetsListener { v, insets ->
                val result = v.onApplyWindowInsets(insets)
                val isImeVisible = insets.isVisible(WindowInsets.Type.ime())
                if (!isImeVisible) {
                    mainHandler.post { sembunyikanSystemBars() }
                }
                result
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
                if ((visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    mainHandler.post { sembunyikanSystemBars() }
                }
            }
        }
    }

    private fun sembunyikanSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(
                    WindowInsets.Type.statusBars()
                        or WindowInsets.Type.navigationBars()
                        or WindowInsets.Type.captionBar()
                )
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
    }

    private fun tampilkanPilihanKeluar() {
        val opsi = arrayOf("Input PIN Pengawas", "Scan QR Code Pengawas")
        AlertDialog.Builder(this)
            .setTitle("Keluar dari Ujian")
            .setItems(opsi) { _, which ->
                when (which) {
                    0 -> tampilkanDialogPinKeluar()
                    1 -> mulaiScanKeluar()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun tampilkanDialogPinKeluar() {
        val inputPin = EditText(this).apply {
            hint = "Masukkan PIN keluar dari pengawas"
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(36, 28, 36, 28)
        }

        AlertDialog.Builder(this)
            .setTitle("Verifikasi PIN Keluar")
            .setMessage("Masukkan PIN keluar aktif hari ini:")
            .setView(inputPin)
            .setPositiveButton("KELUAR") { _, _ ->
                val pinInput = inputPin.text.toString().trim()
                if (pinInput.isEmpty()) {
                    Toast.makeText(this, "PIN tidak boleh kosong", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                verifikasiKodeKeluar(pinInput)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun mulaiScanKeluar() {
        sedangKeluarAtauScan = true
        mainHandler.removeCallbacks(pendingPauseAlarmRunnable)

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

    private fun verifikasiKodeKeluar(kodeDimasukkan: String) {
        Toast.makeText(this, "Memeriksa PIN/kode keluar...", Toast.LENGTH_SHORT).show()
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
                    if (aktif && kodeBenar.isNotEmpty() && kodeDimasukkan.equals(kodeBenar, ignoreCase = true)) {
                        sedangKeluarAtauScan = true
                        mainHandler.removeCallbacks(pendingPauseAlarmRunnable)
                        hentikanAlarmJikaAda()
                        try {
                            stopLockTask()
                        } catch (_: Exception) {}
                        finish()
                    } else {
                        Toast.makeText(
                            this,
                            "PIN atau QR Code keluar tidak sesuai / tidak aktif",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Gagal cek PIN keluar (periksa koneksi internet)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    private fun picuAlarmPelanggaran(pesan: String) {
        if (isFinishing || sedangKeluarAtauScan) return
        Toast.makeText(this, pesan, Toast.LENGTH_LONG).show()
        if (!alarmAktif) {
            alarmAktif = true
            mainHandler.removeCallbacks(alarmRunnable)
            mainHandler.post(alarmRunnable)
        }
    }

    private fun hentikanAlarmJikaAda() {
        alarmAktif = false
        mainHandler.removeCallbacks(alarmRunnable)
        mainHandler.removeCallbacks(pendingPauseAlarmRunnable)
        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        } catch (_: Exception) {}
        toneGenerator = null
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            mainHandler.removeCallbacks(pendingPauseAlarmRunnable)
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
        if (!sedangKeluarAtauScan && !isFinishing) {
            mainHandler.removeCallbacks(pendingPauseAlarmRunnable)
            mainHandler.postDelayed(pendingPauseAlarmRunnable, 500)
        }
    }

    override fun onResume() {
        super.onResume()
        mainHandler.removeCallbacks(pendingPauseAlarmRunnable)
        sembunyikanSystemBars()
        aktifkanLockTaskAman()
    }

    override fun onDestroy() {
        hentikanAlarmJikaAda()
        super.onDestroy()
    }
}
