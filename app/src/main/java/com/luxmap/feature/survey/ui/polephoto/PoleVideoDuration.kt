package com.luxmap.feature.survey.ui.polephoto

private const val MINIMUM_DURATION_MS = 3_000L
private const val MAXIMUM_DURATION_MS = 30_500L

fun isValidPoleVideoDuration(durationMs: Long): Boolean = durationMs in MINIMUM_DURATION_MS..MAXIMUM_DURATION_MS
