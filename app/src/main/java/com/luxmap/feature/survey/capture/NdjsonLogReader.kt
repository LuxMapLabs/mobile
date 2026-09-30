package com.luxmap.feature.survey.capture

import java.io.File

object NdjsonLogReader {
    // Skips the schema header (line 0) and drops the last line if it does not parse as a
    // complete JSON object — the periodic flush in NdjsonLogWriter can leave a partial line on
    // an app crash (spec §12).
    fun readDataLines(file: File): List<String> {
        val allLines = file.readLines()
        if (allLines.isEmpty()) return emptyList()
        val dataLines = allLines.drop(1)
        return if (dataLines.isNotEmpty() && !isCompleteJsonObject(dataLines.last())) {
            dataLines.dropLast(1)
        } else {
            dataLines
        }
    }

    private fun isCompleteJsonObject(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.startsWith("{") && trimmed.endsWith("}")
    }
}
