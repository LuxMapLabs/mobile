package com.luxmap.feature.map.data

import kotlinx.coroutines.flow.Flow

// One dataset flow instead of 2 separate ones (FM-35) — poles and road segments are always
// consumed together on the map screen, so bundle them at the repository boundary. Signature
// still leaves room for a bbox param when FM-15 wires up GET /api/v1/poles?bbox= for real — no
// extra param added yet at this prototype step (see plan FM-06).
interface MapRepository {
    fun observeGisMapDataset(): Flow<GisMapDataset>
}
