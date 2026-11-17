package com.luxmap.feature.survey.ui.polephoto

import kotlin.math.roundToInt

enum class ConfidenceBand { HIGH, MEDIUM, LOW }

private const val HIGH_CONFIDENCE = 0.85
private const val MEDIUM_CONFIDENCE = 0.60

fun confidenceBand(confidence: Double?): ConfidenceBand =
    when {
        confidence == null -> ConfidenceBand.LOW
        confidence >= HIGH_CONFIDENCE -> ConfidenceBand.HIGH
        confidence >= MEDIUM_CONFIDENCE -> ConfidenceBand.MEDIUM
        else -> ConfidenceBand.LOW
    }

fun confidenceLabel(confidence: Double?): String? {
    if (confidence == null) return null
    val wording =
        when (confidenceBand(confidence)) {
            ConfidenceBand.HIGH -> "Tin cậy cao"
            ConfidenceBand.MEDIUM -> "Tin cậy vừa"
            ConfidenceBand.LOW -> "Tin cậy thấp"
        }
    return "$wording (${(confidence * 100).roundToInt()}%)"
}

private fun isConclusive(cvState: String?) = cvState == "on" || cvState == "off"

fun effectiveCvState(
    cvState: String?,
    reviewedCvState: String?,
): String? = reviewedCvState ?: cvState.takeIf(::isConclusive)

fun verdictSourceText(
    cvState: String?,
    reviewedCvState: String?,
): String =
    when {
        reviewedCvState != null && reviewedCvState == cvState -> "Bạn đã xác nhận cùng AI"
        reviewedCvState != null && isConclusive(cvState) -> "Bạn đã sửa. AI nhận định ${cvStateLabel(cvState)}"
        reviewedCvState != null && cvState == null -> "Bạn đã xác nhận. AI không xử lý được"
        reviewedCvState != null -> "Bạn đã xác nhận. AI: Không xác định"
        isConclusive(cvState) -> "AI nhận định"
        cvState == null -> "AI không xử lý được. Hãy chọn trạng thái đèn."
        else -> "AI: Không xác định"
    }

fun reviewActionLabel(
    selected: String?,
    aiCvState: String?,
): String = if (selected != null && selected == aiCvState) "Đồng ý với AI" else "Lưu nhãn đã sửa"

fun cvStateLabel(cvState: String?): String =
    when (cvState) {
        "on" -> "Đèn sáng"
        "off" -> "Đèn tắt"
        null -> "AI không xử lý được"
        else -> "Không xác định"
    }

internal const val GALLERY_MEDIA_PREFIX = "gallery-"

internal fun mediaCaptureSource(filePath: String): String =
    if (filePath.substringAfterLast('/').startsWith(GALLERY_MEDIA_PREFIX)) "gallery" else "camera"

internal fun mediaReplacementLabel(
    mediaType: String,
    source: String,
): String =
    when {
        mediaType == "video" && source == "gallery" -> "Chọn video khác"
        mediaType == "video" -> "Quay lại"
        source == "gallery" -> "Chọn ảnh khác"
        else -> "Chụp lại"
    }
