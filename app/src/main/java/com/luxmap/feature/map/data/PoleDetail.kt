package com.luxmap.feature.map.data

import com.luxmap.BuildConfig
import com.luxmap.core.theme.AssetCondition
import com.luxmap.feature.map.data.dto.PoleDetailDto
import com.luxmap.feature.map.data.dto.PoleDetailFaultDto
import com.luxmap.feature.map.data.dto.PoleDetailFrameDto
import com.luxmap.feature.map.data.dto.PoleDetailLuminancePointDto
import com.luxmap.feature.map.data.dto.PoleDetailRuntimePointDto

// Domain model for FM-27 (pole detail from map) — kept close to PoleDetailDto since this
// screen shows almost every field from GET /api/v1/poles/{pole_id}, only converting status
// strings to the shared AssetCondition enum for reuse with StatusBadge.
data class PoleDetail(
    val poleId: String,
    val segmentId: String,
    val segmentName: String,
    val lat: Double,
    val lng: Double,
    val fixtureType: String,
    val powerSource: String,
    val lampWatt: Int,
    val installDate: String,
    val warrantyExpiry: String,
    val fixtureStatus: AssetCondition,
    val statusConfidence: Double,
    val determinedAt: String,
    val sourceChannel: String,
    val hasIotNode: Boolean,
    val iotNodeStatus: String?,
    val iotLastReportAt: String?,
    val luminanceHistory: List<PoleLuminancePoint>,
    val runtimeHistory: List<PoleRuntimePoint>,
    val openFaults: List<PoleDetailFault>,
    val recentFrames: List<PoleDetailFrame>,
)

data class PoleLuminancePoint(
    val observedAt: String,
    val baselineRatio: Double,
    val classifiedAs: AssetCondition,
)

data class PoleRuntimePoint(
    val nightOf: String,
    val runtimeHours: Double,
)

data class PoleDetailFault(
    val faultId: String,
    val faultType: String,
    val severity: String,
    val faultStatus: String,
)

data class PoleDetailFrame(
    val frameId: String,
    val capturedAt: String,
    val thumbnailUrl: String,
)

fun PoleDetailDto.toPoleDetail(): PoleDetail =
    PoleDetail(
        poleId = poleId,
        segmentId = segmentId,
        segmentName = segmentName,
        lat = location.lat,
        lng = location.lng,
        fixtureType = fixture.fixtureType,
        powerSource = fixture.powerSource,
        lampWatt = fixture.lampWatt,
        installDate = fixture.installDate,
        warrantyExpiry = fixture.warrantyExpiry,
        fixtureStatus = currentStatus.fixtureStatus.toAssetCondition(),
        statusConfidence = currentStatus.statusConfidence,
        determinedAt = currentStatus.determinedAt,
        sourceChannel = currentStatus.sourceChannel,
        hasIotNode = iotNode != null,
        iotNodeStatus = iotNode?.nodeStatus,
        iotLastReportAt = iotNode?.lastReportAt,
        luminanceHistory = luminanceHistory.map { it.toPoleLuminancePoint() },
        runtimeHistory = runtimeHistory.map { it.toPoleRuntimePoint() },
        openFaults = openFaults.map { it.toPoleDetailFault() },
        recentFrames = recentFrames.map { it.toPoleDetailFrame() },
    )

private fun PoleDetailLuminancePointDto.toPoleLuminancePoint() =
    PoleLuminancePoint(
        observedAt = observedAt,
        baselineRatio = baselineRatio,
        classifiedAs = classifiedAs.toAssetCondition(),
    )

private fun PoleDetailRuntimePointDto.toPoleRuntimePoint() =
    PoleRuntimePoint(nightOf = nightOf, runtimeHours = runtimeHours)

private fun PoleDetailFaultDto.toPoleDetailFault() =
    PoleDetailFault(faultId = faultId, faultType = faultType, severity = severity, faultStatus = faultStatus)

private fun PoleDetailFrameDto.toPoleDetailFrame() =
    PoleDetailFrame(frameId = frameId, capturedAt = capturedAt, thumbnailUrl = thumbnailUrl.toAbsoluteUrl())

private fun String.toAssetCondition(): AssetCondition =
    when (this) {
        "normal" -> AssetCondition.NORMAL
        "dim" -> AssetCondition.DIM
        "out" -> AssetCondition.OUT
        else -> AssetCondition.UNKNOWN
    }

// Backend returns thumbnail_url as a path relative to the API host ("/api/v1/frames/.../
// thumbnail"), not a full URL — Coil needs an absolute one to actually load it. Left as-is if
// it's already absolute, so this stays safe if the backend starts returning full URLs later.
private fun String.toAbsoluteUrl(): String {
    if (startsWith("http://") || startsWith("https://")) return this
    val base = BuildConfig.API_BASE_URL.trimEnd('/')
    val path = if (startsWith("/")) this else "/$this"
    return base + path
}
