package com.example.revroom.core.utils

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ImageCompressorTest {
    @Test
    fun rejectsUnknownDimensionsBeforeDecode() {
        assertThrows(SelectedImageException::class.java) {
            ImageCompressor.calculateSampleSize(-1, -1)
        }
        assertThrows(SelectedImageException::class.java) {
            ImageCompressor.calculateSampleSize(1600, 0)
        }
    }

    @Test
    fun acceptsPixelLimitButRejectsOneRowBeyondIt() {
        assertEquals(8, ImageCompressor.calculateSampleSize(8000, 8000))
        assertThrows(SelectedImageException::class.java) {
            ImageCompressor.calculateSampleSize(8000, 8001)
        }
    }

    @Test
    fun rejectsExtremePanoramaEvenWhenPixelCountIsSmall() {
        assertThrows(SelectedImageException::class.java) {
            ImageCompressor.calculateSampleSize(32_769, 1)
        }
        assertThrows(SelectedImageException::class.java) {
            ImageCompressor.calculateSampleSize(Int.MAX_VALUE, Int.MAX_VALUE)
        }
    }

    @Test
    fun keepsSmallImagesAndSamplesPortraitAndLandscapeSymmetrically() {
        assertEquals(1, ImageCompressor.calculateSampleSize(1600, 1200))
        assertEquals(2, ImageCompressor.calculateSampleSize(3200, 1600))
        assertEquals(2, ImageCompressor.calculateSampleSize(1600, 3200))
    }

    @Test
    fun accountsForRoundedUpSampledPixels() {
        assertEquals(4, ImageCompressor.calculateSampleSize(3201, 1600))
    }

    @Test
    fun outputLimitAcceptsExactCapacityAndRejectsExtraByte() {
        val bytes = ByteArrayOutputStream()
        val output = ImageCompressor.SizeLimitedOutputStream(bytes, 4)
        output.write(byteArrayOf(1, 2, 3))
        output.write(4)

        assertThrows(SelectedImageException::class.java) { output.write(5) }
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), bytes.toByteArray())
    }

    @Test
    fun outputLimitRejectsOversizedChunkBeforeWritingAnyOfIt() {
        val bytes = ByteArrayOutputStream()
        val output = ImageCompressor.SizeLimitedOutputStream(bytes, 4)
        output.write(1)

        assertThrows(SelectedImageException::class.java) {
            output.write(byteArrayOf(2, 3, 4, 5))
        }
        assertArrayEquals(byteArrayOf(1), bytes.toByteArray())
    }
}
