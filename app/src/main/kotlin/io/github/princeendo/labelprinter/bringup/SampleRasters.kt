// SPDX-License-Identifier: GPL-3.0-only

package io.github.princeendo.labelprinter.bringup

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.princeendo.labelprinter.printer.MonoRaster
import io.github.princeendo.labelprinter.printer.P12Profile
import io.github.princeendo.labelprinter.printer.TestPatterns
import kotlin.math.ceil

/**
 * Converts a landscape design, where `u` runs along the tape and `v` runs across it from the top
 * edge, into print-head orientation by rotating it 90° counterclockwise, as Print Master does: the
 * top edge goes to dot 0 and the end of the design prints first.
 */
fun landscapeToRaster(
    lengthDots: Int,
    heightDots: Int,
    isBurned: (u: Int, v: Int) -> Boolean,
): MonoRaster = MonoRaster.create(heightDots, lengthDots) { x, y -> isBurned(lengthDots - 1 - y, x) }

/** Joins head-orientation [parts] along the tape with [gapRows] blank rows between them, centering each across [widthDots]. */
fun stackAlongTape(
    parts: List<MonoRaster>,
    widthDots: Int,
    gapRows: Int,
): MonoRaster {
    require(parts.isNotEmpty()) { "nothing to stack" }
    require(parts.all { it.widthDots <= widthDots }) { "a part is wider than $widthDots dots" }
    val starts = parts.runningFold(0) { row, part -> row + part.rows + gapRows }
    val rows = starts.last() - gapRows
    return MonoRaster.create(widthDots, rows) { x, y ->
        val index = starts.indexOfLast { it <= y }
        val part = parts[index]
        val partY = y - starts[index]
        val partX = x - (widthDots - part.widthDots) / 2
        partY < part.rows && partX in 0 until part.widthDots && part[partX, partY]
    }
}

/** A QR code with no quiet zone, each module [moduleDots] square. The tape margins and gaps supply the quiet zone. */
fun qrRaster(
    text: String,
    moduleDots: Int,
): MonoRaster {
    val hints = mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    return MonoRaster.create(matrix.width * moduleDots, matrix.height * moduleDots) { x, y ->
        matrix[x / moduleDots, y / moduleDots]
    }
}

/** Dots per typographic point at the P12's resolution. */
const val DOTS_PER_POINT: Double = P12Profile.DOTS_PER_INCH / 72.0

/**
 * Renders one line of [text] at [sizePoints], reading along the tape and centered across a band
 * [heightDots] tall, using the platform's default sans-serif face. Anti-aliased pixels darker than
 * mid-grey burn.
 */
fun textRaster(
    text: String,
    sizePoints: Double,
    heightDots: Int,
): MonoRaster {
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = (sizePoints * DOTS_PER_POINT).toFloat()
            typeface = Typeface.SANS_SERIF
        }
    val length = ceil(paint.measureText(text)).toInt() + 2
    val bitmap = Bitmap.createBitmap(length, heightDots, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val metrics = paint.fontMetrics
    val baseline = heightDots / 2f - (metrics.ascent + metrics.descent) / 2f
    canvas.drawText(text, 1f, baseline, paint)

    val pixels = IntArray(length * heightDots)
    bitmap.getPixels(pixels, 0, length, 0, 0, length, heightDots)
    bitmap.recycle()
    return landscapeToRaster(length, heightDots) { u, v ->
        val pixel = pixels[v * length + u]
        val luminance = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
        luminance < 128
    }
}

/** The bring-up text and QR sample: 6 pt and 10 pt text, then QR codes with 3- and 4-dot modules. */
fun textAndQrSample(profile: P12Profile): MonoRaster {
    val band = profile.printableWidthDots
    val gap = profile.mmToRows(3.0)
    val content =
        stackAlongTape(
            listOf(
                textRaster("6 pt Hamburgefonts 0123456789", 6.0, band),
                textRaster("10 pt Hamburgefonts 0123", 10.0, band),
                qrRaster("P12 QR 3-DOT", 3),
                qrRaster("P12 QR 4-DOT", 4),
            ),
            band,
            gap,
        )
    return TestPatterns.withTrailer(profile.toHeadRaster(content))
}

/** Draws a head raster landscape, as it reads on the tape, inverting [landscapeToRaster]. */
fun previewBitmap(raster: MonoRaster): Bitmap {
    val pixels =
        IntArray(raster.rows * raster.widthDots) { i ->
            val u = i % raster.rows
            val v = i / raster.rows
            if (raster[v, raster.rows - 1 - u]) Color.BLACK else Color.WHITE
        }
    return Bitmap.createBitmap(pixels, raster.rows, raster.widthDots, Bitmap.Config.ARGB_8888)
}
