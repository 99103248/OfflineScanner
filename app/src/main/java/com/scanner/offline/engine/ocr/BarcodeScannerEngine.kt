package com.scanner.offline.engine.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class BarcodeHit(
    val rawValue: String,
    val format: String
)

@Singleton
class BarcodeScannerEngine @Inject constructor() {

    private val client by lazy { BarcodeScanning.getClient() }

    suspend fun scan(bitmap: Bitmap): List<BarcodeHit> = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        client.process(image)
            .addOnSuccessListener { codes ->
                cont.resume(codes.mapNotNull { b ->
                    val v = b.rawValue ?: b.displayValue ?: return@mapNotNull null
                    BarcodeHit(v, formatName(b.format))
                })
            }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    private fun formatName(format: Int): String = when (format) {
        Barcode.FORMAT_QR_CODE -> "QR"
        Barcode.FORMAT_CODE_128 -> "Code128"
        Barcode.FORMAT_CODE_39 -> "Code39"
        Barcode.FORMAT_EAN_13 -> "EAN-13"
        Barcode.FORMAT_EAN_8 -> "EAN-8"
        Barcode.FORMAT_UPC_A -> "UPC-A"
        Barcode.FORMAT_DATA_MATRIX -> "DataMatrix"
        Barcode.FORMAT_PDF417 -> "PDF417"
        else -> "Barcode"
    }
}
