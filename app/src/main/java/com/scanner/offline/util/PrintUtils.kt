package com.scanner.offline.util

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.scanner.offline.domain.model.PdfPaperSize
import com.scanner.offline.engine.export.DocumentExporter
import java.io.FileOutputStream

/** 调起 Android 系统打印服务，把文档页面打印为 PDF 或直接送打印机。 */
object PrintUtils {

    fun printDocument(
        context: Context,
        jobName: String,
        imagePaths: List<String>,
        paperSize: PdfPaperSize = PdfPaperSize.A4
    ) {
        if (imagePaths.isEmpty()) return
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = PdfPrintAdapter(jobName, imagePaths, paperSize)
        manager.print(jobName, adapter, PrintAttributes.Builder().build())
    }

    private class PdfPrintAdapter(
        private val jobName: String,
        private val imagePaths: List<String>,
        private val paperSize: PdfPaperSize
    ) : PrintDocumentAdapter() {

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder("${jobName.ifBlank { "document" }}.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(imagePaths.size)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            try {
                FileOutputStream(destination.fileDescriptor).use { os ->
                    DocumentExporter().imagesToPdf(imagePaths, os, paperSize)
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message ?: "打印失败")
            }
        }
    }
}
