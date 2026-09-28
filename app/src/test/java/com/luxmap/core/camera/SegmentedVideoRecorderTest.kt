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

        // First frame establishes this segment's own start (presentationTimeUs is continuous across
        // the whole encoder lifetime, never reset per segment — see SegmentedVideoRecorder).
        recorder.onEncodedFrame(isKeyFrame = true, presentationTimeUs = 1_000_000L, sensorTimestampNs = 1_000_000_000L)

        val result =
            recorder.onEncodedFrame(
                isKeyFrame = true,
                presentationTimeUs = 1_000_000L + 181_000_000L,
                sensorTimestampNs = 182_000_000_000L,
            )

        assertEquals(VideoSegmentResult(segmentIndex = 0, filePath = "/tmp/segment_0.mp4", sizeBytes = 4_096L), result)
    }

    @Test
    fun `a new segment after rotation does not immediately re-rotate on a continuing large pts`() {
        val recorder = SegmentedVideoRecorder(policy) { _ -> FakeMuxerPort().apply { closedSizeBytes = 4_096L } }
        recorder.startSegment(segmentIndex = 0, outputFilePath = "/tmp/segment_0.mp4")

        // Establish segment 0's baseline, then rotate once its duration exceeds the target.
        recorder.onEncodedFrame(isKeyFrame = true, presentationTimeUs = 1_000_000L, sensorTimestampNs = 1_000_000_000L)
        recorder.onEncodedFrame(
            isKeyFrame = true,
            presentationTimeUs = 1_000_000L + 181_000_000L,
            sensorTimestampNs = 182_000_000_000L,
        )

        // The next frame lands in the new segment, but presentationTimeUs keeps climbing from the
        // same continuous encoder (never reset) — this must not look like segment 1 already
        // exceeded its own budget just because the encoder's pts is already large.
        val result =
            recorder.onEncodedFrame(
                isKeyFrame = true,
                presentationTimeUs = 1_000_000L + 181_000_000L + 1_000_000L,
                sensorTimestampNs = 183_000_000_000L,
            )

        assertNull(result)
    }

    @Test
    fun `the triggering keyframe is written to the new segment, not the one that just closed`() {
        val muxers = mutableListOf<FakeMuxerPort>()
        val recorder = SegmentedVideoRecorder(policy) { _ -> FakeMuxerPort().also { muxers.add(it) } }
        recorder.startSegment(segmentIndex = 0, outputFilePath = "/tmp/segment_0.mp4")

        recorder.onEncodedFrame(isKeyFrame = true, presentationTimeUs = 1_000_000L, sensorTimestampNs = 1_000_000_000L)
        recorder.onEncodedFrame(
            isKeyFrame = true,
            presentationTimeUs = 1_000_000L + 181_000_000L,
            sensorTimestampNs = 182_000_000_000L,
        )

        assertEquals(2, muxers.size)
        assertEquals(1, muxers[0].written) // old segment: only the baseline frame
        assertEquals(1, muxers[1].written) // new segment: the triggering keyframe, not zero
    }

    @Test
    fun `does not rotate before the target duration`() {
        val muxer = FakeMuxerPort()
        val recorder = SegmentedVideoRecorder(policy) { _ -> muxer }
        recorder.startSegment(segmentIndex = 0, outputFilePath = "/tmp/segment_0.mp4")

        recorder.onEncodedFrame(isKeyFrame = true, presentationTimeUs = 1_000_000L, sensorTimestampNs = 1_000_000_000L)

        val result =
            recorder.onEncodedFrame(
                isKeyFrame = true,
                presentationTimeUs = 1_000_000L + 1_000_000L,
                sensorTimestampNs = 2_000_000_000L,
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
