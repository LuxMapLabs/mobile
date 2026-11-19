package com.luxmap.feature.workorder.polegps.data

import android.os.SystemClock
import javax.inject.Inject

open class GpsClock
    @Inject
    constructor() {
        open fun elapsedRealtimeNanos(): Long = SystemClock.elapsedRealtimeNanos()
    }
