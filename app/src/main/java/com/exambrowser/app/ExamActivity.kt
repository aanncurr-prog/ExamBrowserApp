private val SHEET_API_URL = "https://script.google.com/macros/s/AKfycbwaTTgYWZPEczW6o1p_Zfg0uRr6QgEw_p1sVuS5JUyD7FZSizWZGU-hs6BX2MRix8JSWg/exec"
private var sedangKeluarAtauScan = false

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
                    hentikanAlarmJikaAda()
                    try {
                        stopLockTask()
                    } catch (_: Exception) {}
                    finish()
                } else {
                    Toast.makeText(this, "QR Code keluar tidak valid atau tidak aktif", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Gagal cek kode keluar (periksa koneksi internet)", Toast.LENGTH_SHORT).show()
            }
        }
    }.start()
}
