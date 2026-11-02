package com.example.revroom.core.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

object FileUtils {
    /** Returns a bounded JPEG in the cache. The caller must upload as image/jpeg and delete it. */
    suspend fun uriToFile(context: Context, uri: Uri): File? {
        var temporaryFile: File? = null
        return try {
            withContext(Dispatchers.IO) {
                val imageBytes = ImageCompressor.readAndCompressIfNeeded(context, uri)
                currentCoroutineContext().ensureActive()
                val result = File.createTempFile("avatar-", ".jpg", context.cacheDir)
                temporaryFile = result
                result.outputStream().use { it.write(imageBytes) }
                currentCoroutineContext().ensureActive()
                result
            }
        } catch (error: CancellationException) {
            // Also clean up when cancellation happens while returning from Dispatchers.IO.
            withContext(NonCancellable + Dispatchers.IO) { temporaryFile?.delete() }
            throw error
        } catch (error: IOException) {
            withContext(NonCancellable + Dispatchers.IO) { temporaryFile?.delete() }
            if (error is SelectedImageException) throw error
            throw SelectedImageException("Không thể lưu ảnh tạm. Vui lòng kiểm tra dung lượng thiết bị.", error)
        } catch (error: Exception) {
            withContext(NonCancellable + Dispatchers.IO) { temporaryFile?.delete() }
            throw error
        }
    }
}
