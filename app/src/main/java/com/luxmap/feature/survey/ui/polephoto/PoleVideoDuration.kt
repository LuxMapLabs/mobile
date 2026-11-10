package com.luxmap.feature.survey.ui.polephoto

private const val MINIMUM_DURATION_MS = 3_000L
private const val MAXIMUM_DURATION_MS = 30_500L
private const val MAXIMUM_SIZE_BYTES = 100L * 1024 * 1024

fun isValidPoleVideoDuration(durationMs: Long): Boolean = durationMs in MINIMUM_DURATION_MS..MAXIMUM_DURATION_MS

fun isValidPoleVideoSize(sizeBytes: Long): Boolean = sizeBytes in 1..MAXIMUM_SIZE_BYTES
