package com.denggl2.mason.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfDocumentTextExtractorInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun textLayerIsExtractedButBitmapOnlyPageFallsBackToVision() {
        val textPdf = createPdf { canvas ->
            canvas.drawText("MASON PDF DEVICE TEST", 48f, 96f, textPaint())
        }
        val scannedPdf = createPdf { canvas ->
            val bitmap = Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                drawColor(Color.WHITE)
                drawText("SCANNED PAGE", 32f, 128f, textPaint())
            }
            canvas.drawBitmap(bitmap, 48f, 48f, null)
            bitmap.recycle()
        }

        val extractedText = PdfDocumentTextExtractor.extract(context, textPdf).text
        val extractedScan = PdfDocumentTextExtractor.extract(context, scannedPdf).text

        assertTrue(extractedText.contains("MASON PDF DEVICE TEST"))
        assertTrue(extractedText.hasUsefulPdfText())
        assertFalse(extractedScan.hasUsefulPdfText())
    }

    private fun createPdf(draw: (Canvas) -> Unit): ByteArray {
        val document = PdfDocument()
        return try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(612, 792, 1).create())
            draw(page.canvas)
            document.finishPage(page)
            ByteArrayOutputStream().use { output ->
                document.writeTo(output)
                output.toByteArray()
            }
        } finally {
            document.close()
        }
    }

    private fun textPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 32f
    }
}
