package com.pirlruc.finsilo.ui.settings

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.pirlruc.finsilo.domain.model.RealizedGainsReport
import com.pirlruc.finsilo.domain.usecase.RealizedGainsCsv
import java.io.ByteArrayOutputStream

/** Printable PDF with the same FIFO figures as [RealizedGainsCsv]. */
object RealizedGainsPdf {
    fun write(report: RealizedGainsReport): ByteArray {
        val document = PdfDocument()
        val paint = Paint().apply { textSize = 10f }
        val title = Paint().apply {
            textSize = 14f
            isFakeBoldText = true
        }
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = 40f
        canvas.drawText("Mais-valias FIFO ${report.year}", 40f, y, title)
        y += 24f
        val lines = RealizedGainsCsv.write(report).lineSequence()
        for (line in lines) {
            if (y > 800f) {
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = 40f
            }
            canvas.drawText(line.take(110), 40f, y, paint)
            y += 14f
        }
        document.finishPage(page)
        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }
}
