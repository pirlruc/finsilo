package com.pirlruc.finsilo.ui.settings

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.pirlruc.finsilo.domain.performance.IrsAnnualCsv
import com.pirlruc.finsilo.domain.performance.IrsAnnualRow
import java.io.ByteArrayOutputStream

/** Printable PDF of the annual IRS rows. This is not the official XML and not tax advice. */
object IrsAnnualPdf {
    fun write(rows: List<IrsAnnualRow>): ByteArray {
        val document = PdfDocument()
        val paint = Paint().apply { textSize = 10f }
        val title = Paint().apply {
            textSize = 14f
            isFakeBoldText = true
        }
        val info = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(info)
        val canvas = page.canvas
        var y = 40f
        canvas.drawText("IRS annual rows. Not tax advice. Not the official XML.", 40f, y, title)
        y += 24f
        for (line in IrsAnnualCsv.write(rows).lineSequence()) {
            if (y > 800f) break
            canvas.drawText(line.take(MAX_CHARS), 40f, y, paint)
            y += 14f
        }
        document.finishPage(page)
        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }

    private const val MAX_CHARS: Int = 90
}
