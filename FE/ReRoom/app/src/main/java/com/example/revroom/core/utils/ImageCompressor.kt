package com.example.revroom.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Prepares a single-frame JPEG without loading the original file into memory. */
object ImageCompressor {
    const val OUTPUT_MIME_TYPE = "image/jpeg"
    private const val MAX_SOURCE_BYTES = 32L * 1024 * 1024
    private const val MAX_SOURCE_PIXELS = 64_000_000L
    private const val MAX_SOURCE_DIMENSION = 32_768
    private const val MAX_DIMENSION = 1600
    private const val MAX_OUTPUT_BYTES = 5 * 1024 * 1024
    private const val COMPRESS_QUALITY = 85

    /**
     * Always returns JPEG bytes (including for PNG/WebP input). The source is capped at
     * 32 MiB / 64 MP, decoding is sampled to at most 1600px per side, and output is
     * capped at 5 MiB so the same preparation is safe for design and avatar uploads.
     */
    suspend fun readAndCompressIfNeeded(context: Context, imageUri: Uri): ByteArray =
        withContext(Dispatchers.IO) {
            var sourceFile: File? = null
            try {
                currentCoroutineContext().ensureActive()
                val cachedSource = File.createTempFile("image-source-", ".tmp", context.cacheDir)
                sourceFile = cachedSource
                copySource(context, imageUri, cachedSource)

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(cachedSource.absolutePath, bounds)
                val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
                currentCoroutineContext().ensureActive()

                var bitmap = decodeBitmap(cachedSource, sampleSize, bounds.outMimeType)
                try {
                    currentCoroutineContext().ensureActive()
                    if (bitmap.width > MAX_DIMENSION || bitmap.height > MAX_DIMENSION) {
                        throw SelectedImageException("Không thể thu nhỏ ảnh an toàn. Vui lòng chọn ảnh khác.")
                    }
                    // JPEG has no transparency. Give transparent PNG/WebP regions a white background.
                    if (bitmap.hasAlpha()) {
                        val opaque = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                        opaque.density = bitmap.density
                        try {
                            Canvas(opaque).apply {
                                drawColor(Color.WHITE)
                                drawBitmap(bitmap, 0f, 0f, null)
                            }
                        } catch (error: Throwable) {
                            opaque.recycle()
                            throw error
                        }
                        bitmap.recycle()
                        bitmap = opaque
                    }

                    currentCoroutineContext().ensureActive()
                    val output = ByteArrayOutputStream()
                    val boundedOutput = SizeLimitedOutputStream(output, MAX_OUTPUT_BYTES)
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESS_QUALITY, boundedOutput)) {
                        throw SelectedImageException("Không thể chuyển ảnh sang JPEG. Vui lòng chọn ảnh khác.")
                    }
                    currentCoroutineContext().ensureActive()
                    output.toByteArray().also {
                        if (it.isEmpty()) throw SelectedImageException("Ảnh sau xử lý bị trống.")
                    }
                } finally {
                    bitmap.recycle()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: SelectedImageException) {
                throw error
            } catch (error: SecurityException) {
                throw SelectedImageException("Không còn quyền đọc ảnh. Vui lòng chọn lại ảnh.", error)
            } catch (error: IOException) {
                throw SelectedImageException("Không thể đọc hoặc lưu ảnh đã chọn. Vui lòng chọn lại ảnh.", error)
            } finally {
                sourceFile?.delete()
            }
        }

    private suspend fun copySource(context: Context, imageUri: Uri, destination: File) {
        val input = context.contentResolver.openInputStream(imageUri)
            ?: throw SelectedImageException("Không thể mở ảnh đã chọn. Vui lòng chọn lại ảnh.")
        input.use { source ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_SOURCE_BYTES) {
                        throw SelectedImageException("Ảnh gốc không được vượt quá 32 MB.")
                    }
                    output.write(buffer, 0, count)
                }
                if (total == 0L) throw SelectedImageException("Ảnh đã chọn bị trống.")
            }
        }
    }

    internal fun calculateSampleSize(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) {
            throw SelectedImageException("Tệp đã chọn không phải ảnh được hỗ trợ.")
        }
        if (width > MAX_SOURCE_DIMENSION || height > MAX_SOURCE_DIMENSION ||
            width.toLong() * height > MAX_SOURCE_PIXELS
        ) {
            throw SelectedImageException("Ảnh có kích thước quá lớn. Vui lòng chọn ảnh tối đa 64 megapixel.")
        }
        var sampleSize = 1
        // Use ceiling division: decoders may round a partial sampled pixel upwards.
        while ((width + sampleSize - 1) / sampleSize > MAX_DIMENSION ||
            (height + sampleSize - 1) / sampleSize > MAX_DIMENSION
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun decodeBitmap(source: File, sampleSize: Int, mimeType: String?): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies encoded orientation and handles modern Android image formats.
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(sampleSize)
            }
        }

        val bitmap = BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: throw SelectedImageException("Không thể giải mã ảnh đã chọn.")
        try {
            // API 24–27: preserve the camera JPEG rotation/mirroring when stripping EXIF.
            val orientation = if (mimeType == "image/jpeg") {
                ExifInterface(source.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } else {
                ExifInterface.ORIENTATION_NORMAL
            }
            val transform = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> {
                        setRotate(90f)
                        postScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> {
                        setRotate(-90f)
                        postScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            if (transform.isIdentity) return bitmap
            val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
            if (oriented !== bitmap) bitmap.recycle()
            return oriented
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
    }

    internal class SizeLimitedOutputStream(
        private val delegate: OutputStream,
        private val maxBytes: Int,
    ) : OutputStream() {
        private var count = 0

        override fun write(value: Int) {
            requireCapacity(1)
            delegate.write(value)
            count++
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            requireCapacity(length)
            delegate.write(bytes, offset, length)
            count += length
        }

        private fun requireCapacity(length: Int) {
            if (length > maxBytes - count) {
                throw SelectedImageException("Ảnh sau xử lý vượt quá 5 MB. Vui lòng chọn ảnh nhỏ hơn.")
            }
        }
    }
}

class SelectedImageException(message: String, cause: Throwable? = null) : IOException(message, cause)
