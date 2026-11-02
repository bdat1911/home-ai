package com.example.revroom.data.repository

import com.example.revroom.core.utils.SelectedImageException
import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignRepositoryExceptionTest {
    @Test fun invalidImagesAndRejectedRequestsAreNotAmbiguousSubmissions() {
        assertTrue(DesignRepositoryException("too large", cause = SelectedImageException("too large")).isInputRejection)
        assertTrue(DesignRepositoryException("bad options", httpStatus = 400).isInputRejection)
        assertTrue(DesignRepositoryException("unsupported image", httpStatus = 415).isInputRejection)
    }

    @Test fun lostConnectionAndServerFailuresMayFollowAnAcceptedJob() {
        assertFalse(DesignRepositoryException("offline", cause = IOException()).isInputRejection)
        assertFalse(DesignRepositoryException("server error", httpStatus = 500).isInputRejection)
    }
}
