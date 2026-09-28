package com.luxmap.feature.survey.capture

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.Timer
import java.util.TimerTask

// schema_version is a header LINE on .ndjson files, unlike manifest.json/capture_config.json
// where it is a JSON field (spec §8). flushIntervalMs periodic flush (spec §12) bounds data loss
// on a crash to at most that interval.
//
// The periodic flush runs on the Timer's own thread while appendLine() is called from a recorder's
// thread/coroutine — BufferedWriter is not thread-safe, so every write AND every flush must take
// the same lock, or a flush landing mid-write can persist a torn line. lock (not `this`) is used
// explicitly so the intent is not hidden behind a bare @Synchronized on a class with a Timer field.
class NdjsonLogWriter(
    file: File,
    fileRole: String,
    flushIntervalMs: Long = 1_000L,
) {
    private val lock = Any()
    private val writer: BufferedWriter = BufferedWriter(FileWriter(file, true))

    // isDaemon = true so the timer does not prevent the app from exiting
    private val flushTimer = Timer(true)

    // Timer.cancel() does not interrupt a TimerTask that already started running -- without this
    // flag, a flush already in flight when close() runs can still acquire the lock afterward and
    // call flush() on an already-closed writer, throwing an uncaught IOException on the Timer
    // thread (which crashes the process on Android). Only read/written under `lock`.
    private var closed = false

    init {
        synchronized(lock) {
            writer.write("""{"schema_version":"v0","file_role":"$fileRole"}""")
            writer.newLine()
            writer.flush()
        }
        flushTimer.scheduleAtFixedRate(
            object : TimerTask() {
                override fun run() =
                    synchronized(lock) {
                        if (!closed) writer.flush()
                    }
            },
            flushIntervalMs,
            flushIntervalMs,
        )
    }

    fun appendLine(json: String) {
        synchronized(lock) {
            writer.write(json)
            writer.newLine()
        }
    }

    fun close() {
        flushTimer.cancel()
        synchronized(lock) {
            closed = true
            writer.flush()
            writer.close()
        }
    }
}
