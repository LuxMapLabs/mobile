package com.luxmap.feature.survey.capture

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import com.luxmap.core.camera.MuxerPort
import java.io.File
import java.nio.ByteBuffer

// outputFormat must be the encoder's REAL output format, read only after the encoder produced
// output (see VideoCaptureSession.openFirstSegment). For H.264 that format carries csd-0/csd-1
// (SPS/PPS); addTrack() with a format that has no csd gives a file no player can decode.
class RealMuxerPort(
    private val outputFilePath: String,
    outputFormat: MediaFormat,
    orientationDegrees: Int,
) : MuxerPort {
    private val muxer =
        MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).apply {
            // The encoder always gets the pixels the way the sensor reads them out, so the file is
            // landscape even when the phone was held upright. This writes the needed rotation into
            // the MP4 header and the player turns the picture on playback; no pixel is touched, so
            // it costs nothing. Must be set before start(), which writeSample() does lazily below.
            setOrientationHint(orientationDegrees)
        }
    private val trackIndex = muxer.addTrack(outputFormat)
    private var started = false
    private var pendingBuffer: ByteBuffer? = null
    private var pendingBufferInfo: MediaCodec.BufferInfo? = null

    // MuxerPort.writeSample() only takes a timestamp because that is all the pure rotation test
    // needed. The real bytes for that same call are staged here right before it, so Task 5 stays
    // unchanged and still muxes real data. This is a deliberate adapter, not a mismatch.
    fun setPendingSample(
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo,
    ) {
        pendingBuffer = buffer
        pendingBufferInfo = bufferInfo
    }

    override fun writeSample(presentationTimeUs: Long) {
        // start() must come after addTrack() and before the first writeSampleData(). Doing it
        // lazily here also means a segment that never got a frame is closed without a start().
        if (!started) {
            muxer.start()
            started = true
        }
        val buffer = requireNotNull(pendingBuffer) { "setPendingSample() must be called before writeSample()" }
        val info = requireNotNull(pendingBufferInfo) { "setPendingSample() must be called before writeSample()" }
        muxer.writeSampleData(trackIndex, buffer, info)
    }

    override fun close(): Long {
        if (started) muxer.stop()
        muxer.release()
        return File(outputFilePath).length()
    }
}
