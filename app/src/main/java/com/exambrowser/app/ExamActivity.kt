package com.exambrowser.app

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class ExamActivity : AppCompatActivity() {

    private val PIN_PENGAWAS = "123456"

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var warningOverlay: LinearLayout
    private lateinit var tvWarningDetail: TextView

    private var isAuthorizedExit = false
    private var isAlarmActive = false
    private var toneGenerator: ToneGenerator? = null
    private val alarmHandler = Handler(Looper.getMainLooper())

    private val alarmRunnable = object : Runnable {
        override fun run() {
            if (isAlarmActive) {
                try {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 400)
                } catch (_: Exception) {}
                alarmHandler.postDelayed(this, 600)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_exam)
        aktifkanFullscreen()

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        warningOverlay = findViewById(R.id.warningOverlay)
        tvWarningDetail = findViewById(R.id.tvWarningDetail)

        toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)

        val examUrl = intent.getStringExtra("EXAM_URL") ?: "https://google.com"

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.userAgentString = "${settings.userAgentString} ExamBrowserApp/1.0"

        webView.setOnLongClickListener { true }
        webView.isLongClickable = false

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress == 100) View.GONE else View.VISIBLE
            }
        }

        webView.loadUrl(examUrl)

        findViewById<Button>(R.id.btnReload).setOnClickListener {
            webView.reload()
        }

        findViewById<Button>(R.id.btnKeluar).setOnClickListener {
            tampilkanDialogPinKeluar()
        }

        findViewById<Button>(R.id.btnAcknowledge).setOnClickListener {
            hentikanAlarm()
            aktifkanLockTask()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@ExamActivity, "Gunakan tombol Keluar (PIN Pengawas)", Toast.LENGTH_SHORT).show()
            }
        })

        aktifkanLockTask()
    }

    private fun aktifkanLockTask() {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
                startLockTask()
            }
        } catch (_: Exception) {}
    }

    private fun aktifkanFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            )
        }
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
        super.onMultiWindowModeChanged(isInMultiWindowMode)
        if (isInMultiWindowMode && !isAuthorizedExit) {
            picuPelanggaran("Terdeteksi Split Screen / Floating Window! Kembalikan ke layar penuh.")
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isAuthorizedExit) {
            picuPelanggaran("Terdeteksi upaya meninggalkan halaman ujian!")
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            aktifkanFullscreen()
        } else if (!isAuthorizedExit) {
            picuPelanggaran("Fokus layar hilang! Dilarang membuka panel notifikasi atau aplikasi lain.")
        }
    }

    private fun picuPelanggaran(pesan: String) {
        tvWarningDetail.text = pesan
        warningOverlay.visibility = View.VISIBLE
        if (!isAlarmActive) {
            isAlarmActive = true
            alarmHandler.post(alarmRunnable)
        }
    }

    private fun hentikanAlarm() {
        isAlarmActive = false
        alarmHandler.removeCallbacks(alarmRunnable)
        warningOverlay.visibility = View.GONE
    }

    private fun tampilkanDialogPinKeluar() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        input.hint = "Masukkan PIN Pengawas"

        AlertDialog.Builder(this)
            .setTitle("Konfirmasi Keluar Ujian")
            .setMessage("Masukkan PIN Pengawas untuk mengakhiri sesi ujian:")
            .setView(input)
            .setPositiveButton("Keluar") { _, _ ->
                if (input.text.toString() == PIN_PENGAWAS) {
                    isAuthorizedExit = true
                    hentikanAlarm()
                    try {
                        stopLockTask()
                    } catch (_: Exception) {}
                    finish()
                } else {
                    Toast.makeText(this, "PIN Salah!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroy() {
        hentikanAlarm()
        toneGenerator?.release()
        super.onDestroy()
    }
}
