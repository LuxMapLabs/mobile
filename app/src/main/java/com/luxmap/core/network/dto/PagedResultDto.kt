package com.luxmap.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Shared paging envelope for every paginated endpoint (CLAUDE.md section 0 — backend
// api-contract-v1.1.md: page, page_size, total, items[]). Not tied to one feature.
@Serializable
data class PagedResultDto<T>(
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    val total: Int,
    val items: List<T>,
)
