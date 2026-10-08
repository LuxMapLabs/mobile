package com.luxmap.feature.survey.data

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeUploadRepositoryTest {
    @Test
    fun `emits increasing progress then Done`() =
        runTest {
            val repository = FakeUploadRepository()

            repository.uploadSession("SESSION-1").test {
                var lastBytesSent = -1L
                var sawDone = false
                while (!sawDone) {
                    when (val progress = awaitItem()) {
                        is UploadProgress.InProgress -> {
                            assertTrue(progress.bytesSent > lastBytesSent)
                            lastBytesSent = progress.bytesSent
                        }
                        is UploadProgress.Done -> sawDone = true
                        is UploadProgress.Failed -> error("unexpected failure: ${progress.reason}")
                        is UploadProgress.Conflict -> error("unexpected conflict: ${progress.reason}")
                    }
                }
                assertTrue(sawDone)
                awaitComplete()
            }
        }

    @Test
    fun `resuming with the same sessionId does not restart from zero`() =
        runTest {
            val repository = FakeUploadRepository()
            repository.markInterruptedAt(sessionId = "SESSION-1", bytesSent = 5_000L)

            repository.uploadSession("SESSION-1").test {
                val first = awaitItem() as UploadProgress.InProgress
                assertTrue(first.bytesSent >= 5_000L)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
