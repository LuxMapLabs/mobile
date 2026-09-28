package com.luxmap.core.camera

// Rotation must land on a keyframe (spec §4/§5) so MediaMuxer can close cleanly without corrupting
// the segment — waiting past targetDurationMs for the next keyframe if none lands exactly on it.
class SegmentRotationPolicy(
    private val targetDurationMs: Long,
) {
    fun shouldRotate(
        currentSegmentDurationMs: Long,
        isKeyFrame: Boolean,
    ): Boolean = currentSegmentDurationMs >= targetDurationMs && isKeyFrame
}
