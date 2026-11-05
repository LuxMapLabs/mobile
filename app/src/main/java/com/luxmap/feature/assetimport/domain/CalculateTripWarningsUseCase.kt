package com.luxmap.feature.assetimport.domain

import com.luxmap.feature.assetimport.data.ImportPole
import javax.inject.Inject
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

const val DUPLICATE_COORD_THRESHOLD_METERS = 8.0
private const val EARTH_RADIUS_METERS = 6_371_000.0

data class TripWarning(val message: String)

data class TripWarningsResult(
    val warnings: List<TripWarning>,
    val perPoleMessages: Map<String, List<String>>,
)

class CalculateTripWarningsUseCase
    @Inject
    constructor() {
        operator fun invoke(poles: List<ImportPole>): TripWarningsResult {
            val withCoords = poles.filter { it.lat != null && it.lng != null }
            val warnings = mutableListOf<TripWarning>()
            val perPole = mutableMapOf<String, MutableList<String>>()

            for (i in withCoords.indices) {
                for (j in i + 1 until withCoords.size) {
                    val a = withCoords[i]
                    val b = withCoords[j]
                    val distance = distanceMeters(a.lat!!, a.lng!!, b.lat!!, b.lng!!)
                    if (distance < DUPLICATE_COORD_THRESHOLD_METERS) {
                        val distanceLabel = formatMeters(distance)
                        warnings +=
                            TripWarning(
                                "Cột #${a.seq} và #${b.seq} cách nhau $distanceLabel — có thể chụp trùng một cột.",
                            )
                        perPole.getOrPut(a.poleEntryId) { mutableListOf() }.add("Cách #${b.seq} $distanceLabel")
                        perPole.getOrPut(b.poleEntryId) { mutableListOf() }.add("Cách #${a.seq} $distanceLabel")
                    }
                }
            }

            poles.filter { it.lat == null || it.lng == null }.forEach { pole ->
                warnings += TripWarning("Cột #${pole.seq} thiếu toạ độ.")
                perPole.getOrPut(pole.poleEntryId) { mutableListOf() }.add("Thiếu toạ độ")
            }

            return TripWarningsResult(warnings, perPole)
        }

        private fun distanceMeters(
            lat1: Double,
            lng1: Double,
            lat2: Double,
            lng2: Double,
        ): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a =
                sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
            return EARTH_RADIUS_METERS * 2 * asin(sqrt(a))
        }

        private fun formatMeters(distance: Double): String = "%.1f m".format(distance).replace(".", ",")
    }
