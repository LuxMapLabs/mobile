package com.luxmap.feature.map.ui

import com.luxmap.feature.map.data.CabinetMarker
import com.luxmap.feature.map.data.CabinetTopology
import com.luxmap.feature.map.data.CabinetTopologyException
import com.luxmap.feature.map.data.MapNetworkException

sealed interface TopologyState {
    data object Loading : TopologyState

    data class Loaded(val topology: CabinetTopology) : TopologyState

    data class Error(val message: String) : TopologyState
}

data class CabinetSelection(
    val cabinet: CabinetMarker,
    val topology: TopologyState,
)

internal fun Throwable.topologyErrorText(): String =
    when (this) {
        is CabinetTopologyException.NotInScope -> "Tủ này không thuộc địa bàn của bạn"
        is CabinetTopologyException.PolesWithoutSegment -> "Có cột chưa gắn tuyến, chưa vẽ được sơ đồ"
        is MapNetworkException -> "Không tải được sơ đồ cấp nguồn. Kiểm tra kết nối rồi thử lại."
        else -> "Không tải được sơ đồ cấp nguồn"
    }
