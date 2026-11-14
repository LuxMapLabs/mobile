package com.luxmap.feature.workorder.polegps.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val UNSAFE_SEGMENT = Regex("[^A-Za-z0-9_-]")

fun sanitizeSegment(value: String): String = value.replace(UNSAFE_SEGMENT, "_")

@Singleton
class PoleGpsFiles(
    private val root: File,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(File(context.filesDir, "pole-gps"))

    fun fileFor(
        workOrderId: String,
        attachmentId: String,
    ): File = File(File(root, sanitizeSegment(workOrderId)), "${sanitizeSegment(attachmentId)}.pdf")

    fun deleteOrder(workOrderId: String) {
        File(root, sanitizeSegment(workOrderId)).deleteRecursively()
    }
}
