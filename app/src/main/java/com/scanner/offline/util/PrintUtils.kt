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
import java.util.concurrent.Executors

/** 调起 Android 系统打印服务，把文档页面打印为 PDF 或直接送打印机。 */
object PrintUtils {

    private val printExecutor = Executors.newSingleThreadExecutor()

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
            if (cancellationSignal?.isCanceled == true) {
                runCatching { destination.close() }
                callback.onWriteCancelled()
                return
            }
            printExecutor.execute {
                try {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onWriteCancelled()
                        return@execute
                    }
                    FileOutputStream(destination.fileDescriptor).use { os ->
                        DocumentExporter().imagesToPdf(imagePaths, os, paperSize)
                    }
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onWriteCancelled()
                    } else {
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    }
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message ?: "打印失败")
                } finally {
                    runCatching { destination.close() }
                }
            }
        }
    }
}
