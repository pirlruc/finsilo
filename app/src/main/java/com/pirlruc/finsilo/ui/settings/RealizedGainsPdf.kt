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
        var pageNumber = 1
        fun start(): PdfDocument.Page {
            val info = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
            pageNumber += 1
            return document.startPage(info)
        }
        var page = start()
        var canvas = page.canvas
        var y = 40f
        fun advance() {
            if (y <= 800f) return
            document.finishPage(page)
            page = start()
            canvas = page.canvas
            y = 40f
        }
        canvas.drawText("Mais-valias FIFO ${report.year}", 40f, y, title)
        y += 24f
        val lines = RealizedGainsCsv.write(report).lineSequence().flatMap { line ->
            line.chunked(90).asSequence()
        }
        for (line in lines) {
            advance()
            canvas.drawText(line, 40f, y, paint)
            y += 14f
        }
        document.finishPage(page)
        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }
}
