package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luxmap.core.common.openInMaps
import com.luxmap.core.common.poleDistanceLabel
import com.luxmap.core.common.poleLampLabel
import com.luxmap.core.common.polePositionLabel
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.Spacing

// Shared presentation for both field lists; callbacks keep navigation owned by the screens.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoleIdentificationDetails(
    poleId: String,
    position: Int?,
    segmentId: String?,
    note: String?,
    fixtureType: String?,
    lampWatt: Int?,
    lampCount: Int?,
    fixtureCount: Int?,
    nearSensitivePoi: Boolean?,
    lat: Double?,
    lng: Double?,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
    phoneLat: Double? = null,
    phoneLng: Double? = null,
) {
    var expanded by rememberSaveable(poleId) { mutableStateOf(false) }
    val context = LocalContext.current
    val positionLabel = polePositionLabel(position, segmentId)
    val lampLabel = poleLampLabel(fixtureType, lampWatt, lampCount, fixtureCount)
    Row(modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            positionLabel?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            note?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            lampLabel?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            poleDistanceLabel(phoneLat, phoneLng, lat, lng)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            if (nearSensitivePoi == true) StatusBadge(text = "Gần trường/cầu", colors = LuxTheme.status.kind)
        }
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Thông tin cột $poleId")
        }
    }
    if (expanded) {
        ModalBottomSheet(onDismissRequest = { expanded = false }) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(poleId, style = MaterialTheme.typography.titleMedium)
                positionLabel?.let { Text(it) }
                note?.takeIf { it.isNotBlank() }?.let { Text(it) }
                lampLabel?.let { Text(it) }
                TextButton(
                    onClick = {
                        expanded = false
                        onOpenMap()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Xem trên bản đồ") }
                if (lat != null && lng != null) {
                    TextButton(
                        onClick = { openInMaps(context, lat, lng) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Đường đi") }
                }
            }
        }
    }
}
