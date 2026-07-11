package com.invictus.link

import android.app.Activity
import android.widget.Button
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView

/**
 * Portrait QR scanner for bridge pairing — replaces the stock ZXing capture
 * screen that locks landscape and hides an obvious way back to Link.
 */
class LinkQrCaptureActivity : CaptureActivity() {
    override fun initializeContent(): DecoratedBarcodeView {
        setContentView(R.layout.link_qr_capture)
        findViewById<Button>(R.id.zxing_back_button).setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        return findViewById(R.id.zxing_barcode_scanner)
    }
}
