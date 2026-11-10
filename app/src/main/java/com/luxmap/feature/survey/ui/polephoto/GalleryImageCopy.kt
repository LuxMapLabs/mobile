package com.luxmap.feature.survey.ui.polephoto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

suspend fun copyGalleryImageToFile(
    input: InputStream?,
    destination: File,
): Boolean =
    withContext(Dispatchers.IO) {
        if (input == null) {
            false
        } else {
            runCatching {
                input.use { source -> destination.outputStream().use { output -> source.copyTo(output) } }
            }.isSuccess
        }
    }
