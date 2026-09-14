package com.example.revroom.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Compresses images larger than 10MB down to max 1080px dimension.
 * Used before uploading to prevent oversized payloads.
 */
object ImageCompressor {
    private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10MB
    private const val MAX_DIMENSION = 1080
    private const val COMPRESS_QUALITY = 85

    /**
     * Reads image from [imageUri]. If size > 10MB, resizes to max 1080px and
     * returns JPEG-compressed bytes. Otherwise returns original bytes.
     */
    fun readAndCompressIfNeeded(context: Context, imageUri: Uri): ByteArray {
        val contentResolver = context.contentResolver
        val inputBytes = contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
            ?: throw IOException("Cannot read selected image.")

        if (inputBytes.size <= MAX_FILE_SIZE_BYTES) {
            return inputBytes
        }

        // Decode bounds only
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size, boundsOptions)

        // Calculate inSampleSize
        var inSampleSize = 1
        while (boundsOptions.outWidth / inSampleSize > MAX_DIMENSION ||
            boundsOptions.outHeight / inSampleSize > MAX_DIMENSION
        ) {
            inSampleSize *= 2
        }

        // Decode with inSampleSize
        val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        val bitmap = BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size, decodeOptions)
            ?: throw IOException("Cannot decode image for compression.")

        // Scale to exact max dimension if still larger
        val scaledBitmap = scaleBitmapIfNeeded(bitmap, MAX_DIMENSION)

        val output = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESS_QUALITY, output)

        if (scaledBitmap !== bitmap) scaledBitmap.recycle()
        bitmap.recycle()

        return output.toByteArray()
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap

        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        return Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }
}
