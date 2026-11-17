package com.luxmap.feature.map.data

sealed class CabinetTopologyException(cause: Throwable? = null) : Exception(cause) {
    class NotInScope(cause: Throwable? = null) : CabinetTopologyException(cause)

    class PolesWithoutSegment(cause: Throwable? = null) : CabinetTopologyException(cause)

    class Failed(cause: Throwable? = null) : CabinetTopologyException(cause)
}
