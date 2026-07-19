package com.magic.pagetime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import androidx.exifinterface.media.ExifInterface

object ImageComposer {

    var lastFontDebugInfo: String = ""

    fun composeImage(
        context: Context,
        baseImagePath: String,
        apiValue: String,
        marginLeftPct: Int,
        marginRightPct: Int,
        marginTopPct: Int,
        marginBottomPct: Int,
        textColorHex: String,
        opacityPct: Int,
        rotationDeg: Float,
        textSizePx: Float,
        pageNumberText: String
    ): Bitmap {
        val original = loadAndCorrectOrientation(baseImagePath)
        val bitmap = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bitmap)
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        val boxLeft = w * (marginLeftPct / 100f)
        val boxRight = w * (1f - marginRightPct / 100f)
        val boxTop = h * (marginTopPct / 100f)
        val boxBottom = h * (1f - marginBottomPct / 100f)
        val centerX = (boxLeft + boxRight) / 2f
        val centerY = (boxTop + boxBottom) / 2f

        val typeface = loadPrintTypeface(context)
        val boldTypeface = try { Typeface.create(typeface, Typeface.BOLD) } catch (e: Exception) { typeface }

        val baseColor = try { Color.parseColor(textColorHex) } catch (e: Exception) { Color.parseColor("#1C1C1E") }
        val alpha = ((opacityPct.coerceIn(0, 100)) / 100f * 255).toInt()
        val colorWithAlpha = Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        bodyPaint.typeface = typeface
        bodyPaint.textSize = textSizePx
        bodyPaint.color = colorWithAlpha

        val pageNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        pageNumberPaint.typeface = boldTypeface
        pageNumberPaint.textSize = textSizePx * 0.9f
        pageNumberPaint.color = colorWithAlpha

        val lines = AcrosticGenerator.generate(context, apiValue)

        val fm = bodyPaint.fontMetrics
        val lineHeight = (fm.descent - fm.ascent) * 1.2f

        // Render all text onto its own transparent layer first, so it can be
        // blurred to match the base photo's natural camera softness before
        // being composited — crisp vector text on a real photo is a dead
        // giveaway up close.
        val textLayer = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val textCanvas = Canvas(textLayer)

        textCanvas.save()
        textCanvas.rotate(rotationDeg, centerX, centerY)
        textCanvas.clipRect(boxLeft, boxTop, boxRight, boxBottom)

        val pageNumFm = pageNumberPaint.fontMetrics
        val pageNumberBaselineY = boxTop - pageNumFm.ascent
        if (pageNumberText.isNotBlank()) {
            textCanvas.drawText(pageNumberText, boxLeft, pageNumberBaselineY, pageNumberPaint)
        }

        var currentY = if (pageNumberText.isNotBlank()) {
            pageNumberBaselineY + (lineHeight * 2f)
        } else {
            boxTop - fm.ascent + (lineHeight * 2f)
        }

        for (entry in lines) {
            if (!entry.isBlank && entry.text.isNotEmpty()) {
                textCanvas.drawText(entry.text, boxLeft, currentY, bodyPaint)
            }
            currentY += lineHeight
        }

        textCanvas.restore()

        val softenedTextLayer = softenLayer(textLayer, strength = 0.6f)
        canvas.drawBitmap(softenedTextLayer, 0f, 0f, null)

        return bitmap
    }

    private fun softenLayer(source: Bitmap, strength: Float): Bitmap {
        val scale = (1f - strength.coerceIn(0f, 0.6f))
        val smallW = (source.width * scale).toInt().coerceAtLeast(1)
        val smallH = (source.height * scale).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(source, smallW, smallH, true)
        val restored = Bitmap.createScaledBitmap(small, source.width, source.height, true)
        small.recycle()
        return restored
    }

    private fun loadAndCorrectOrientation(path: String): Bitmap {
        val original = BitmapFactory.decodeFile(path)
            ?: throw IllegalStateException("Base image not found")

        val orientation = try {
            val exif = ExifInterface(path)
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> { }
        }

        return if (!matrix.isIdentity) {
            Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        } else {
            original
        }
    }

    private fun loadPrintTypeface(context: Context): Typeface {
        return try {
            val assetManager = context.assets
            val fontFiles = assetManager.list("fonts") ?: emptyArray()

            if (fontFiles.isEmpty()) {
                lastFontDebugInfo = "No files found in assets/fonts/"
                return Typeface.SANS_SERIF
            }

            val fontFile = fontFiles.firstOrNull {
                it.endsWith(".ttf", ignoreCase = true) || it.endsWith(".otf", ignoreCase = true)
            }

            if (fontFile == null) {
                lastFontDebugInfo = "No .ttf/.otf found: ${fontFiles.joinToString()}"
                return Typeface.SANS_SERIF
            }

            lastFontDebugInfo = "Loaded $fontFile"
            Typeface.createFromAsset(assetManager, "fonts/$fontFile")
        } catch (e: Exception) {
            lastFontDebugInfo = "Font load FAILED: ${e.javaClass.simpleName} - ${e.message}"
            Typeface.SANS_SERIF
        }
    }
}
