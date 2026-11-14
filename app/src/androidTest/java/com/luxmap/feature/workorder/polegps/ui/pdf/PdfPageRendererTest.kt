package com.luxmap.feature.workorder.polegps.ui.pdf

import android.graphics.pdf.PdfDocument
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class PdfPageRendererTest {
    private fun pdfFile(
        pages: Int,
        size: Int = 200,
    ): File {
        val document = PdfDocument()
        repeat(pages) { i ->
            val info = PdfDocument.PageInfo.Builder(size, size, i + 1).create()
            val page = document.startPage(info)
            document.finishPage(page)
        }
        val file =
            File.createTempFile("test", ".pdf", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    @Test
    fun rendering_several_pages_concurrently_does_not_crash() =
        runBlocking {
            val pageCount = 8
            val renderer = PdfPageRenderer(pdfFile(pages = pageCount, size = 1500))
            try {
                val bitmaps =
                    withContext(Dispatchers.IO) {
                        (0 until pageCount).map { index -> async { renderer.renderPage(index) } }.awaitAll()
                    }
                assertEquals(pageCount, bitmaps.size)
                assertTrue(bitmaps.all { it.width > 0 })
            } finally {
                renderer.close()
            }
        }

    @Test
    fun rendering_after_close_fails_cleanly_instead_of_crashing_native_code() {
        val renderer = PdfPageRenderer(pdfFile(pages = 1))
        renderer.close()
        try {
            renderer.renderPage(0)
            fail("expected renderPage to fail after close")
        } catch (e: IllegalStateException) {
            // expected - a clear Kotlin exception, not a native crash from a closed PdfRenderer
        }
    }

    @Test
    fun opening_a_corrupt_file_throws_a_catchable_exception() {
        val file =
            File.createTempFile("bad", ".pdf", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        try {
            PdfPageRenderer(file)
            fail("expected the corrupt file to fail to open")
        } catch (e: Exception) {
            // expected - an IOException from PdfRenderer, caught by the composable that opens this
        }
    }
}
