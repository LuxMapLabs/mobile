package com.luxmap.feature.workorder.polegps.ui.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val RENDER_SCALE = 2

class PdfPageRenderer(file: File) : AutoCloseable {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val lock = ReentrantLock()
    private var closed = false

    val pageCount: Int get() = renderer.pageCount

    fun renderPage(index: Int): Bitmap =
        lock.withLock {
            check(!closed) { "PdfPageRenderer is closed" }
            val page = renderer.openPage(index)
            try {
                val bitmap =
                    Bitmap.createBitmap(
                        page.width * RENDER_SCALE,
                        page.height * RENDER_SCALE,
                        Bitmap.Config.ARGB_8888,
                    )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } finally {
                page.close()
            }
        }

    override fun close() =
        lock.withLock {
            if (closed) return@withLock
            closed = true
            renderer.close()
            descriptor.close()
        }
}
