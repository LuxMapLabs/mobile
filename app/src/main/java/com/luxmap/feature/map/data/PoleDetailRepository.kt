package com.luxmap.feature.map.data

import kotlinx.coroutines.flow.Flow

// GET /api/v1/poles/{pole_id} (Contract v1.1 §2.2) — separate from MapRepository because it
// loads one pole's full detail + history on demand, not the whole map dataset.
// Returns null when the backend would answer 404 (pole_id not found/out of scope) — the
// ViewModel maps that to PoleDetailUiState.Empty, distinct from a network/parse Error.
interface PoleDetailRepository {
    fun observePoleDetail(poleId: String): Flow<PoleDetail?>
}
