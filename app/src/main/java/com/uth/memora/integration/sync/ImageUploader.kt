package com.uth.memora.integration.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlin.math.roundToInt

/**
 * ============================================================================
 * KHU VỰC CỦA THÀNH VIÊN 3: NÉN & TẢI ẢNH THẺ LÊN FIREBASE STORAGE (Mục 6.3, 10.1 & 11)
 * ============================================================================
 * Đọc ảnh minh họa từ đường dẫn cục bộ (`content://`, `file://` hoặc đường dẫn tệp),
 * thu nhỏ tối đa cạnh dài 1280px (Mục 11), nén JPEG giảm dần quality cho đến khi
 * dung lượng <= 500 KB rồi tải lên Firebase Storage theo đường dẫn:
 * `users/{uid}/card_images/{cardId}.jpg`.
 */
class ImageUploader(
    context: Context,
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val appContext: Context = context.applicationContext

    /**
     * Nén ảnh từ [localImagePath] (cạnh dài <= 1280px, dung lượng <= 500 KB),
     * tải lên `users/{uid}/card_images/{cardId}.jpg` và trả về URL tải xuống (`cloudImageUrl`) (Mục 10.1 & 11).
     */
    suspend fun uploadCardImage(
        uid: String,
        cardId: String,
        localImagePath: String
    ): Result<String> = withContext(ioDispatcher) {
        val cleanUid = uid.trim()
        val cleanCardId = cardId.trim()
        val cleanPath = localImagePath.trim()

        if (cleanUid.isEmpty() || cleanCardId.isEmpty() || cleanPath.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException(ERROR_INVALID_INPUT_VI))
        }

        try {
            // 1. Đọc, xoay EXIF, thu nhỏ cạnh dài tối đa 1280px và nén JPEG <= 500 KB (Mục 11)
            val compressedBytes = decodeResizeAndCompress(cleanPath)

            // 2. Tải lên Firebase Storage tại users/{uid}/card_images/{cardId}.jpg (Mục 10.1)
            val storagePath = buildCardImageStoragePath(cleanUid, cleanCardId)
            val storageRef = storage.reference.child(storagePath)
            val metadata = StorageMetadata.Builder()
                .setContentType(CONTENT_TYPE_JPEG)
                .build()

            storageRef.putBytes(compressedBytes, metadata).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()

            if (downloadUrl.isBlank()) {
                return@withContext Result.failure(IllegalStateException(ERROR_UPLOAD_UNKNOWN_VI))
            }
            Result.success(downloadUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(mapUploadException(e))
        }
    }

    /**
     * Đọc bitmap an toàn bằng 2 lượt (`inJustDecodeBounds` + `inSampleSize` tránh OOM với ảnh vài MB),
     * chuẩn hóa hướng xoay EXIF, thu nhỏ cạnh dài tối đa 1280px và nén JPEG <= 500 KB (Mục 11).
     */
    private fun decodeResizeAndCompress(localImagePath: String): ByteArray {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        openImageInputStream(localImagePath)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        } ?: throw LocalImageUnavailableException(ERROR_IMAGE_NOT_FOUND_VI)

        val rawWidth = boundsOptions.outWidth
        val rawHeight = boundsOptions.outHeight
        if (rawWidth <= 0 || rawHeight <= 0) {
            throw LocalImageUnavailableException(ERROR_INVALID_IMAGE_VI)
        }

        val orientation = readExifOrientation(localImagePath)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(rawWidth, rawHeight, MAX_IMAGE_DIMENSION_PX)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decodedBitmap = openImageInputStream(localImagePath)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw LocalImageUnavailableException(ERROR_INVALID_IMAGE_VI)

        var workingBitmap: Bitmap = decodedBitmap
        try {
            // Xoay ảnh đúng chiều chụp theo EXIF trước khi thu nhỏ (Mục 11)
            val orientedBitmap = applyExifOrientation(workingBitmap, orientation)
            if (orientedBitmap !== workingBitmap) {
                workingBitmap.recycle()
                workingBitmap = orientedBitmap
            }

            // Thu nhỏ cạnh dài tối đa 1280px, giữ nguyên tỷ lệ khung hình (Mục 11)
            val scaledBitmap = scaleDownToMaxDimension(workingBitmap, MAX_IMAGE_DIMENSION_PX)
            if (scaledBitmap !== workingBitmap) {
                workingBitmap.recycle()
                workingBitmap = scaledBitmap
            }

            // Phủ nền trắng cho ảnh PNG có kênh trong suốt trước khi nén JPEG
            val opaqueBitmap = flattenTransparencyToWhite(workingBitmap)
            if (opaqueBitmap !== workingBitmap) {
                workingBitmap.recycle()
                workingBitmap = opaqueBitmap
            }

            return compressBitmapWithinLimit(workingBitmap, MAX_IMAGE_SIZE_BYTES)
        } finally {
            if (!workingBitmap.isRecycled) {
                workingBitmap.recycle()
            }
        }
    }

    /**
     * Nén JPEG giảm dần quality cho đến khi dung lượng <= [maxSizeBytes] (500 KB).
     * Nếu ảnh cực nhiều chi tiết vẫn vượt 500 KB ở mức quality tối thiểu, tự thu nhỏ thêm kích thước để bảo đảm giới hạn (Mục 11).
     */
    private fun compressBitmapWithinLimit(source: Bitmap, maxSizeBytes: Int): ByteArray {
        var currentBitmap: Bitmap = source
        var latestBytes: ByteArray = ByteArray(0)

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

                // Dự phòng: thu nhỏ thêm 80% nếu ảnh quá nhiễu vẫn vượt 500 KB ở quality thấp nhất
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

    private fun scaleDownToMaxDimension(bitmap: Bitmap, maxDimensionPx: Int): Bitmap {
        val (targetWidth, targetHeight) = computeScaledDimensions(
            width = bitmap.width,
            height = bitmap.height,
            maxDimensionPx = maxDimensionPx
        )
        if (targetWidth == bitmap.width && targetHeight == bitmap.height) {
            return bitmap
        }
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

    private fun readExifOrientation(localImagePath: String): Int {
        return runCatching {
            openImageInputStream(localImagePath)?.use { stream ->
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

    /**
     * Mở luồng đọc ảnh hỗ trợ cả URI từ Photo Picker (`content://`), `file://` và đường dẫn tệp cục bộ (Mục 6.3).
     */
    private fun openImageInputStream(localImagePath: String): InputStream? {
        return try {
            when {
                localImagePath.startsWith("content://", ignoreCase = true) ||
                    localImagePath.startsWith("android.resource://", ignoreCase = true) -> {
                    appContext.contentResolver.openInputStream(Uri.parse(localImagePath))
                }

                localImagePath.startsWith("file://", ignoreCase = true) -> {
                    val uri = Uri.parse(localImagePath)
                    val file = uri.path?.let { File(it) }
                    if (file != null && file.exists() && file.isFile) {
                        FileInputStream(file)
                    } else {
                        appContext.contentResolver.openInputStream(uri)
                    }
                }

                else -> {
                    val file = File(localImagePath)
                    if (file.exists() && file.isFile) FileInputStream(file) else null
                }
            }
        } catch (e: SecurityException) {
            throw LocalImageUnavailableException(ERROR_IMAGE_PERMISSION_VI, e)
        } catch (e: FileNotFoundException) {
            throw LocalImageUnavailableException(ERROR_IMAGE_NOT_FOUND_VI, e)
        }
    }

    /**
     * Ngoại lệ cho biết tệp ảnh cục bộ không còn tồn tại, bị thu hồi quyền đọc hoặc bị hỏng.
     */
    internal class LocalImageUnavailableException(
        message: String,
        cause: Throwable? = null
    ) : IOException(message, cause)

    /**
     * Ngoại lệ chuẩn hóa đã gắn thông báo tiếng Việt thân thiện cho lỗi tải ảnh Firebase Storage (Mục 10.1).
     */
    internal class ImageUploadException(
        message: String,
        cause: Throwable? = null
    ) : Exception(message, cause)

    companion object {
        /** Kích thước tối đa cạnh dài ảnh thẻ minh họa: 1280px (Mục 11). */
        const val MAX_IMAGE_DIMENSION_PX: Int = 1280

        /** Dung lượng tối đa ảnh tải lên Firebase Storage: 500 KB (Mục 6.3 & Mục 11). */
        const val MAX_IMAGE_SIZE_BYTES: Int = 500 * 1024

        const val INITIAL_JPEG_QUALITY: Int = 90
        const val MIN_JPEG_QUALITY: Int = 20
        const val JPEG_QUALITY_STEP: Int = 10

        private const val FALLBACK_SCALE_FACTOR: Double = 0.8
        private const val MIN_FALLBACK_DIMENSION_PX: Int = 320
        private const val CONTENT_TYPE_JPEG: String = "image/jpeg"

        const val ERROR_INVALID_INPUT_VI: String =
            "Thiếu thông tin ảnh hoặc mã thẻ để tải lên đám mây."
        const val ERROR_IMAGE_NOT_FOUND_VI: String =
            "Không tìm thấy tệp ảnh minh họa trên thiết bị."
        const val ERROR_IMAGE_PERMISSION_VI: String =
            "Không có quyền đọc ảnh minh họa đã chọn trên thiết bị."
        const val ERROR_INVALID_IMAGE_VI: String =
            "Tệp ảnh không hợp lệ hoặc bị hỏng, không thể đọc được."
        const val ERROR_NETWORK_VI: String =
            "Không có kết nối mạng khi tải ảnh thẻ lên đám mây. Ảnh sẽ tự động tải lại sau."
        const val ERROR_RATE_LIMIT_VI: String =
            "Hệ thống lưu trữ ảnh đang vượt giới hạn yêu cầu (429). Vui lòng thử lại sau."
        const val ERROR_PERMISSION_VI: String =
            "Phiên đăng nhập đã hết hạn hoặc bạn không có quyền tải ảnh lên đám mây."
        const val ERROR_UPLOAD_UNKNOWN_VI: String =
            "Không thể tải ảnh minh họa lên đám mây lúc này. Vui lòng thử lại sau."

        /**
         * Đường dẫn chuẩn lưu ảnh thẻ trên Firebase Storage: `users/{uid}/card_images/{cardId}.jpg` (Mục 10.1).
         */
        fun buildCardImageStoragePath(uid: String, cardId: String): String =
            "${FirestoreCollections.USERS}/$uid/card_images/$cardId.jpg"

        /**
         * Tính hệ số `inSampleSize` (lũy thừa của 2) để giải mã ảnh độ phân giải lớn mà không tràn bộ nhớ (Mục 11).
         */
        internal fun calculateInSampleSize(
            width: Int,
            height: Int,
            maxDimensionPx: Int = MAX_IMAGE_DIMENSION_PX
        ): Int {
            var inSampleSize = 1
            var currentLongEdge = maxOf(width, height)
            while (currentLongEdge / 2 >= maxDimensionPx) {
                inSampleSize *= 2
                currentLongEdge /= 2
            }
            return inSampleSize
        }

        /**
         * Tính kích thước `(width, height)` sau khi thu nhỏ cạnh dài tối đa về [maxDimensionPx], giữ nguyên tỷ lệ (Mục 11).
         */
        internal fun computeScaledDimensions(
            width: Int,
            height: Int,
            maxDimensionPx: Int = MAX_IMAGE_DIMENSION_PX
        ): Pair<Int, Int> {
            val longEdge = maxOf(width, height)
            if (longEdge <= maxDimensionPx || width <= 0 || height <= 0) {
                return width to height
            }
            val scale = maxDimensionPx.toDouble() / longEdge.toDouble()
            val scaledWidth = (width * scale).roundToInt().coerceAtLeast(1)
            val scaledHeight = (height * scale).roundToInt().coerceAtLeast(1)
            return scaledWidth to scaledHeight
        }

        /**
         * Chuẩn hóa lỗi tải ảnh Firebase Storage / mạng / 429 sang thông báo tiếng Việt thân thiện (Mục 10.1).
         */
        internal fun mapUploadException(throwable: Throwable): Exception {
            return when (throwable) {
                is LocalImageUnavailableException -> throwable
                is ImageUploadException -> throwable

                is StorageException -> when (throwable.errorCode) {
                    StorageException.ERROR_QUOTA_EXCEEDED ->
                        ImageUploadException(ERROR_RATE_LIMIT_VI, throwable)

                    StorageException.ERROR_RETRY_LIMIT_EXCEEDED ->
                        ImageUploadException(ERROR_NETWORK_VI, throwable)

                    StorageException.ERROR_NOT_AUTHENTICATED,
                    StorageException.ERROR_NOT_AUTHORIZED ->
                        ImageUploadException(ERROR_PERMISSION_VI, throwable)

                    else -> mapGenericUploadException(throwable)
                }

                is FirebaseTooManyRequestsException ->
                    ImageUploadException(ERROR_RATE_LIMIT_VI, throwable)

                is FirebaseNetworkException ->
                    ImageUploadException(ERROR_NETWORK_VI, throwable)

                else -> mapGenericUploadException(throwable)
            }
        }

        private fun mapGenericUploadException(throwable: Throwable): Exception {
            val causeChain = generateSequence(throwable) { it.cause }.toList()
            val combinedMessage = causeChain
                .mapNotNull { it.message }
                .joinToString(separator = " ")
                .lowercase()

            if (
                combinedMessage.contains("429") ||
                combinedMessage.contains("quota") ||
                combinedMessage.contains("too many requests") ||
                combinedMessage.contains("resource_exhausted")
            ) {
                return ImageUploadException(ERROR_RATE_LIMIT_VI, throwable)
            }

            if (
                causeChain.any { it is IOException } ||
                combinedMessage.contains("network") ||
                combinedMessage.contains("unable to resolve host") ||
                combinedMessage.contains("failed to connect") ||
                combinedMessage.contains("timeout") ||
                combinedMessage.contains("timed out")
            ) {
                return ImageUploadException(ERROR_NETWORK_VI, throwable)
            }

            return ImageUploadException(ERROR_UPLOAD_UNKNOWN_VI, throwable)
        }
    }
}
