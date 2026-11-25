package com.coffemod.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream

data class LabelItem(
    val code: String,
    val name: String,
    val unit: String,
)

private const val PAGE_W = 595
private const val PAGE_H = 842
private const val MARGIN = 24f
private const val COLS = 3
private const val ROWS = 8

fun buildLabelsPdf(items: List<LabelItem>, out: File): File {
    val doc = PdfDocument()
    val cellW = (PAGE_W - 2 * MARGIN) / COLS
    val cellH = (PAGE_H - 2 * MARGIN) / ROWS

    items.chunked(COLS * ROWS).forEachIndexed { pageIndex, pageItems ->
        val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageIndex + 1).create()
        val page = doc.startPage(info)
        val canvas = page.canvas
        pageItems.forEachIndexed { index, item ->
            val col = index % COLS
            val row = index / COLS
            drawLabel(canvas, item, MARGIN + col * cellW, MARGIN + row * cellH, cellW, cellH)
        }
        doc.finishPage(page)
    }

    out.parentFile?.mkdirs()
    FileOutputStream(out).use { doc.writeTo(it) }
    doc.close()
    return out
}

private fun drawLabel(canvas: Canvas, item: LabelItem, x: Float, y: Float, w: Float, h: Float) {
    val border = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
        color = Color.parseColor("#BDB4A8")
    }
    canvas.drawRect(x + 2, y + 2, x + w - 2, y + h - 2, border)

    val qrSize = minOf(h - 20f, 64f)
    val qr = qrBitmap(item.code, qrSize.toInt().coerceAtLeast(48))
    canvas.drawBitmap(qr, x + 6f, y + (h - qrSize) / 2f, null)

    val textLeft = x + 12f + qrSize
    val textWidth = w - (qrSize + 20f)
    val namePaint = Paint().apply {
        color = Color.BLACK
        textSize = 9f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }
    val codePaint = Paint().apply {
        color = Color.DKGRAY
        textSize = 7.5f
        isAntiAlias = true
    }

    val lines = wrapText(item.name, namePaint, textWidth, 2)
    var textY = y + h / 2f - (lines.size - 1) * 5f
    lines.forEach { line ->
        canvas.drawText(line, textLeft, textY, namePaint)
        textY += 10f
    }
    canvas.drawText(item.code + " · " + item.unit, textLeft, textY + 4f, codePaint)
}

private fun wrapText(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
    val words = text.split(' ')
    val lines = mutableListOf<String>()
    var current = ""
    for (word in words) {
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (paint.measureText(candidate) <= maxWidth) {
            current = candidate
        } else {
            if (current.isNotEmpty()) lines += current
            current = word
            if (lines.size == maxLines) break
        }
    }
    if (current.isNotEmpty() && lines.size < maxLines) lines += current
    if (lines.isEmpty()) lines += text.take(18)
    return lines.take(maxLines)
}

private fun qrBitmap(text: String, size: Int): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 0)
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    for (px in 0 until matrix.width) {
        for (py in 0 until matrix.height) {
            bitmap.setPixel(px, py, if (matrix.get(px, py)) Color.BLACK else Color.WHITE)
        }
    }
    return bitmap
}
