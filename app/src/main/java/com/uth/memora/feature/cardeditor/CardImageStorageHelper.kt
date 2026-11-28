package com.uth.memora.feature.cardeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Bộ trợ giúp sao chép và nén ảnh minh họa thẻ từ Photo Picker (`content://`) vào thư mục nội bộ
 * `context.filesDir/card_images/` với dung lượng tối đa 500KB (Mục 6.3 & Mục 11).
 *
 * Lợi ích:
 * - Không phụ thuộc vào giới hạn 128–512 persistable URI permissions của hệ điều hành Android.
 * - Ảnh thẻ vẫn hiển thị bền vững ngay cả khi người dùng xóa ảnh gốc trong Thư viện (Gallery).
 * - Tự động xoay đúng chiều EXIF, thu nhỏ cạnh dài tối đa 1280px và nén JPEG <= 500KB.
 */
object CardImageStorageHelper {

    private const val CARD_IMAGES_DIR_NAME = "card_images"
    private const val MAX_IMAGE_DIMENSION_PX = 1280
    private const val MAX_IMAGE_SIZE_BYTES = 500 * 1024 // 500KB
    private const val INITIAL_JPEG_QUALITY = 90
    private const val MIN_JPEG_QUALITY = 20
    private const val JPEG_QUALITY_STEP = 10
    private const val FALLBACK_SCALE_FACTOR = 0.8
    private const val MIN_FALLBACK_DIMENSION_PX = 320

    /**
     * Đọc ảnh từ [sourceUri], chuẩn hóa kích thước & dung lượng (`<= 500KB`) và lưu vào
     * `context.filesDir/card_images/card_img_<timestamp>_<uuid>.jpg`.
     */
    suspend fun copyAndCompressToInternalStorage(
        context: Context,
        sourceUri: Uri
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val appContext = context.applicationContext
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            appContext.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            } ?: throw IOException("Không thể mở tệp ảnh đã chọn.")

            val rawWidth = boundsOptions.outWidth
            val rawHeight = boundsOptions.outHeight
            if (rawWidth <= 0 || rawHeight <= 0) {
                throw IllegalArgumentException("Tệp ảnh không hợp lệ hoặc bị hỏng.")
            }

            val orientation = readExifOrientation(appContext, sourceUri)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(rawWidth, rawHeight, MAX_IMAGE_DIMENSION_PX)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decodedBitmap = appContext.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: throw IllegalArgumentException("Không thể giải mã tệp ảnh đã chọn.")

            val compressedBytes = processAndCompressBitmap(decodedBitmap, orientation)

            val cardImagesDir = File(appContext.filesDir, CARD_IMAGES_DIR_NAME).apply {
                if (!exists()) mkdirs()
            }
            val fileName = "card_img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
            val destFile = File(cardImagesDir, fileName)

            FileOutputStream(destFile).use { out ->
                out.write(compressedBytes)
                out.flush()
            }

            destFile
        }
    }

    private fun processAndCompressBitmap(decodedBitmap: Bitmap, orientation: Int): ByteArray {
        var workingBitmap: Bitmap = decodedBitmap
        try {
            val oriented = applyExifOrientation(workingBitmap, orientation)
            if (oriented !== workingBitmap) {
                workingBitmap.recycle()
                workingBitmap = oriented
            }

            val scaled = scaleDownToMaxDimension(workingBitmap, MAX_IMAGE_DIMENSION_PX)
            if (scaled !== workingBitmap) {
                workingBitmap.recycle()
                workingBitmap = scaled
            }

            val opaque = flattenTransparencyToWhite(workingBitmap)
            if (opaque !== workingBitmap) {
                workingBitmap.recycle()
                workingBitmap = opaque
            }

            return compressBitmapWithinLimit(workingBitmap, MAX_IMAGE_SIZE_BYTES)
        } finally {
            if (!workingBitmap.isRecycled) {
                workingBitmap.recycle()
            }
        }
    }

    private fun compressBitmapWithinLimit(source: Bitmap, maxSizeBytes: Int): ByteArray {
        var currentBitmap: Bitmap = source
        var latestBytes = ByteArray(0)

        try {
            while (true) {
                var quality = INITIAL_JPEG_QUALITY
                while (quality >= MIN_JPEG_QUALITY) {
                    val output = ByteArrayOutputStream()
                    currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
                    latestBytes = output.toByteArray()
                    if (latestBytes.size <= maxSizeBytes) {
                        return latestBytes
                    }
                    quality -= JPEG_QUALITY_STEP
                }

                val nextWidth = (currentBitmap.width * FALLBACK_SCALE_FACTOR).roundToInt()
                val nextHeight = (currentBitmap.height * FALLBACK_SCALE_FACTOR).roundToInt()
                if (maxOf(nextWidth, nextHeight) < MIN_FALLBACK_DIMENSION_PX) {
                    return latestBytes
                }

                val smaller = Bitmap.createScaledBitmap(
                    currentBitmap,
                    nextWidth.coerceAtLeast(1),
                    nextHeight.coerceAtLeast(1),
                    true
                )
                if (currentBitmap !== source && !currentBitmap.isRecycled) {
                    currentBitmap.recycle()
                }
                currentBitmap = smaller
            }
        } finally {
            if (currentBitmap !== source && !currentBitmap.isRecycled) {
                currentBitmap.recycle()
            }
        }
    }

    private fun readExifOrientation(context: Context, uri: Uri): Int {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDownToMaxDimension(bitmap: Bitmap, maxDimensionPx: Int): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= maxDimensionPx || bitmap.width <= 0 || bitmap.height <= 0) {
            return bitmap
        }
        val scale = maxDimensionPx.toDouble() / longEdge.toDouble()
        val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun flattenTransparencyToWhite(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return result
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimensionPx: Int): Int {
        var inSampleSize = 1
        var currentLongEdge = maxOf(width, height)
        while (currentLongEdge / 2 >= maxDimensionPx) {
            inSampleSize *= 2
            currentLongEdge /= 2
        }
        return inSampleSize
    }
}
