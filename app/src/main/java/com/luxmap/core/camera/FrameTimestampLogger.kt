package com.luxmap.core.camera

import javax.inject.Inject

// sensorTimestampNs comes from Camera2's SENSOR_TIMESTAMP, not SystemClock.elapsedRealtimeNanos()
// directly — kept as its own field name per spec §8 because the two only coincide when
// SENSOR_INFO_TIMESTAMP_SOURCE == REALTIME (confirmed per device by the Task 2 spike).
data class FrameTimestampEntry(
    val frameIndex: Int,
    val sensorTimestampNs: Long,
    val videoPtsUs: Long,
    val segment: Int,
)

class FrameTimestampLogger
    @Inject
    constructor() {
        fun buildEntry(
            frameIndex: Int,
            sensorTimestampNs: Long,
            videoPtsUs: Long,
            segment: Int,
        ): FrameTimestampEntry = FrameTimestampEntry(frameIndex, sensorTimestampNs, videoPtsUs, segment)
    }
