package com.luxmap.feature.survey.capture

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class NdjsonLogReaderTest {
    @Test
    fun `skips the header line and returns only data lines`() {
        val file = File.createTempFile("lux_log", ".ndjson")
        file.writeText(
            """
            {"schema_version":"v0","file_role":"lux_log"}
            {"seq":1,"lux":10.0}
            {"seq":2,"lux":11.0}
            """.trimIndent() + "\n",
        )

        val lines = NdjsonLogReader.readDataLines(file)

        assertEquals(listOf("""{"seq":1,"lux":10.0}""", """{"seq":2,"lux":11.0}"""), lines)
    }

    @Test
    fun `drops a truncated last line left by a crash mid-flush`() {
        val file = File.createTempFile("lux_log", ".ndjson")
        file.writeText(
            """
            {"schema_version":"v0","file_role":"lux_log"}
            {"seq":1,"lux":10.0}
            {"seq":2,"lux":1
            """.trimIndent(),
        )

        val lines = NdjsonLogReader.readDataLines(file)

        assertEquals(listOf("""{"seq":1,"lux":10.0}"""), lines)
    }
}
