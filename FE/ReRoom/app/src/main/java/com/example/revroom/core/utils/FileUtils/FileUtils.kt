package com.example.revroom.core.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object FileUtils {
    // Hàm này làm nhiệm vụ: Lấy cái ảnh từ Uri -> Copy ra một file tạm trong bộ nhớ đệm
    fun uriToFile(context: Context, uri: Uri): File? {
        val contentResolver = context.contentResolver
        // Tạo một file rác tên là avatar_...jpg
        val tempFile = File(context.cacheDir, "avatar_${System.currentTimeMillis()}.jpg")

        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(tempFile)
            inputStream?.copyTo(outputStream)

            inputStream?.close()
            outputStream.close()
            tempFile // Trả về cái file thật
        } catch (e: Exception) {
            null
        }
    }
}