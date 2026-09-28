package com.luxmap.core.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

private class FakeMuxerPort : MuxerPort {
    var closedSizeBytes: Long = 0
    var written = 0
    var failNextWrite = false

    override fun writeSample(presentationTimeUs: Long) {
        if (failNextWrite) throw IOException("disk full")
        written++
    }

    override fun close(): Long = closedSizeBytes
}

class SegmentedVideoRecorderTest {
    private val policy = SegmentRotationPolicy(targetDurationMs = 180_000L)

    @Test
    fun `rotating a segment reports the closed segment's result and starts the next index`() {
        val muxer = FakeMuxerPort().apply { closedSizeBytes = 4_096L }
        val recorder = SegmentedVideoRecorder(policy) { _ -> muxer }
        recorder.startSegment(segmentIndex = 0, outputFilePath = "/tmp/segment_0.mp4")

        val result =
            recorder.onEncodedFrame(
                isKeyFrame = true,
                presentationTimeUs = 181_000_000L,
                sensorTimestampNs = 181_000_000_000L,
            )

        assertEquals(VideoSegmentResult(segmentIndex = 0, filePath = "/tmp/segment_0.mp4", sizeBytes = 4_096L), result)
    }

    @Test
    fun `does not rotate before the target duration`() {
        val muxer = FakeMuxerPort()
        val recorder = SegmentedVideoRecorder(policy) { _ -> muxer }
        recorder.startSegment(segmentIndex = 0, outputFilePath = "/tmp/segment_0.mp4")

        val result =
            recorder.onEncodedFrame(
                isKeyFrame = true,
                presentationTimeUs = 1_000_000L,
                sensorTimestampNs = 1_000_000_000L,
            )

        assertNull(result)
    }

    @Test
    fun `a write failure surfaces as an exception instead of a silently truncated file`() {
        val muxer = FakeMuxerPort().apply { failNextWrite = true }
        val recorder = SegmentedVideoRecorder(policy) { _ -> muxer }
        recorder.startSegment(segmentIndex = 0, outputFilePath = "/tmp/segment_0.mp4")

        assertThrows(IOException::class.java) {
            recorder.onEncodedFrame(isKeyFrame = false, presentationTimeUs = 1_000L, sensorTimestampNs = 1_000L)
        }
    }
}
