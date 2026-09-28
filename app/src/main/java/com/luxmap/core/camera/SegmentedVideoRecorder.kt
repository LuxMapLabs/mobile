package com.luxmap.core.camera

// Thin seam over MediaMuxer so the rotation bookkeeping above is testable without a real encoder.
// The real implementation wraps android.media.MediaMuxer.writeSampleData/stop+release.
interface MuxerPort {
    fun writeSample(presentationTimeUs: Long)

    // Returns the final file size once closed.
    fun close(): Long
}

data class VideoSegmentResult(
    val segmentIndex: Int,
    val filePath: String,
    val sizeBytes: Long,
)

// openMuxer builds the MuxerPort for a given output file path — injected so tests can substitute
// a fake without touching android.media.MediaMuxer.
class SegmentedVideoRecorder(
    private val rotationPolicy: SegmentRotationPolicy,
    private val openMuxer: (outputFilePath: String) -> MuxerPort,
) {
    private var currentSegmentIndex = -1
    private var currentFilePath = ""
    private var currentMuxer: MuxerPort? = null
    private var segmentStartUs = 0L

    fun startSegment(
        segmentIndex: Int,
        outputFilePath: String,
    ) {
        currentSegmentIndex = segmentIndex
        currentFilePath = outputFilePath
        currentMuxer = openMuxer(outputFilePath)
        segmentStartUs = 0L
    }

    // Returns the just-closed segment's result when this frame triggers a rotation, null otherwise.
    fun onEncodedFrame(
        isKeyFrame: Boolean,
        presentationTimeUs: Long,
        sensorTimestampNs: Long,
    ): VideoSegmentResult? {
        val muxer = requireNotNull(currentMuxer) { "startSegment() must be called before onEncodedFrame()" }
        muxer.writeSample(presentationTimeUs)

        val currentDurationMs = (presentationTimeUs - segmentStartUs) / 1000
        if (!rotationPolicy.shouldRotate(currentDurationMs, isKeyFrame)) return null

        val closedResult = closeCurrentSegment()
        startSegment(currentSegmentIndex + 1, nextSegmentPath(currentFilePath, currentSegmentIndex + 1))
        return closedResult
    }

    fun stop(): VideoSegmentResult = closeCurrentSegment()

    private fun closeCurrentSegment(): VideoSegmentResult {
        val muxer = requireNotNull(currentMuxer) { "No active segment to close" }
        val sizeBytes = muxer.close()
        return VideoSegmentResult(currentSegmentIndex, currentFilePath, sizeBytes)
    }

    private fun nextSegmentPath(
        previousPath: String,
        nextIndex: Int,
    ): String = previousPath.replaceAfterLast("segment_", "$nextIndex.mp4")
}
