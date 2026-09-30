package com.luxmap.feature.survey.capture

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class NdjsonLogWriterTest {
    @Test
    fun `writes a schema header line first, then each appended line`() {
        val file = File.createTempFile("gps_track", ".ndjson")
        val writer = NdjsonLogWriter(file, fileRole = "gps_track")

        writer.appendLine("""{"elapsed_realtime_ns":1,"lat":10.0,"lng":106.0}""")
        writer.appendLine("""{"elapsed_realtime_ns":2,"lat":10.1,"lng":106.1}""")
        writer.close()

        val lines = file.readLines()
        assertEquals("""{"schema_version":"v0","file_role":"gps_track"}""", lines[0])
        assertEquals(3, lines.size)
    }

    // A late GPS callback can reach appendLine() after the service closed the writer. It runs on the
    // main thread, so a throw here would crash the app instead of being caught by a coroutine scope.
    @Test
    fun `drops a line appended after close instead of throwing`() {
        val file = File.createTempFile("gps_track_late", ".ndjson")
        val writer = NdjsonLogWriter(file, fileRole = "gps_track")

        writer.appendLine("""{"elapsed_realtime_ns":1}""")
        writer.close()
        writer.appendLine("""{"elapsed_realtime_ns":2}""")

        val lines = file.readLines()
        assertEquals(2, lines.size)
    }
}
