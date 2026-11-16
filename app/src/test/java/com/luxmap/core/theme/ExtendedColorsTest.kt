package com.luxmap.core.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtendedColorsTest {
    private val themes =
        listOf(
            Triple("light", LightExtendedColors, LuxMapLightColorScheme.surface),
            Triple("night", NightFieldExtendedColors, LuxMapDarkColorScheme.surface),
        )

    private fun assertContrast(
        label: String,
        foreground: Color,
        background: Color,
        minimum: Double,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$label was $ratio, needs $minimum", ratio >= minimum)
    }

    @Test
    fun `feedback text roles reach AA on the theme surface`() {
        themes.forEach { (name, colors, surface) ->
            assertContrast("$name success", colors.success, surface, 4.5)
            assertContrast("$name warning", colors.warning, surface, 4.5)
            assertContrast("$name info", colors.info, surface, 4.5)
            assertContrast("$name dueOverdue", colors.dueOverdue, surface, 4.5)
            assertContrast("$name dueToday", colors.dueToday, surface, 4.5)
            assertContrast("$name dueFuture", colors.dueFuture, surface, 4.5)
        }
    }

    @Test
    fun `feedback containers carry AAA text`() {
        themes.forEach { (name, colors, _) ->
            assertContrast("$name onSuccessContainer", colors.onSuccessContainer, colors.successContainer, 7.0)
            assertContrast("$name onWarningContainer", colors.onWarningContainer, colors.warningContainer, 7.0)
            assertContrast("$name onInfoContainer", colors.onInfoContainer, colors.infoContainer, 7.0)
            assertContrast("$name onSuccess", colors.onSuccess, colors.success, 4.5)
            assertContrast("$name onWarning", colors.onWarning, colors.warning, 4.5)
            assertContrast("$name onInfo", colors.onInfo, colors.info, 4.5)
        }
    }

    @Test
    fun `disabled content stays readable and focus ring is visible`() {
        themes.forEach { (name, colors, surface) ->
            assertContrast("$name disabled", colors.disabledContent, colors.disabledContainer, 4.5)
            assertContrast("$name focusRing", colors.focusRing, surface, 3.0)
        }
    }

    @Test
    fun `card text reaches AAA on the card container in both themes`() {
        assertContrast("light card", LuxMapLightColorScheme.onSurface, LightExtendedColors.cardContainer, 7.0)
        assertContrast("night card", LuxMapDarkColorScheme.onSurface, NightFieldExtendedColors.cardContainer, 7.0)
        assertContrast(
            "light card secondary",
            LuxMapLightColorScheme.onSurfaceVariant,
            LightExtendedColors.cardContainer,
            4.5,
        )
        assertContrast(
            "night card secondary",
            LuxMapDarkColorScheme.onSurfaceVariant,
            NightFieldExtendedColors.cardContainer,
            4.5,
        )
    }

    @Test
    fun `due today is readable in night field, unlike the old fixed amber`() {
        assertContrast("night dueToday", NightFieldExtendedColors.dueToday, LuxMapDarkColorScheme.surface, 7.0)
    }

    @Test
    fun `every status palette covers every enum value`() {
        listOf("light" to LightStatusColors, "night" to NightFieldStatusColors).forEach { (name, status) ->
            assertEquals("$name asset", AssetCondition.entries.toSet(), status.asset.keys)
            assertEquals("$name workOrder", WorkOrderStatus.entries.toSet(), status.workOrder.keys)
            assertEquals("$name priority", WorkOrderPriority.entries.toSet(), status.priority.keys)
            assertEquals("$name sync", SyncStatus.entries.toSet(), status.sync.keys)
            assertEquals("$name iot", IotStatus.entries.toSet(), status.iot.keys)
        }
    }

    @Test
    fun `every badge reaches AA in both themes`() {
        listOf("light" to LightStatusColors, "night" to NightFieldStatusColors).forEach { (name, status) ->
            val badges =
                status.asset.mapKeys { "asset.${it.key}" } +
                    status.workOrder.mapKeys { "wo.${it.key}" } +
                    status.priority.mapKeys { "priority.${it.key}" } +
                    status.sync.mapKeys { "sync.${it.key}" } +
                    status.iot.mapKeys { "iot.${it.key}" } +
                    mapOf("kind" to status.kind)
            badges.forEach { (key, colors) ->
                assertContrast("$name $key", colors.text, colors.background, 4.5)
            }
        }
    }

    @Test
    fun `light asset and sync palettes keep the v2 hex pairs so light screens do not shift`() {
        assertEquals(
            BadgeColors(Color(0xFFD1FAE5), Color(0xFF065F46)),
            LightStatusColors.asset.getValue(AssetCondition.NORMAL),
        )
        assertEquals(
            BadgeColors(Color(0xFFFEF3C7), Color(0xFF92400E)),
            LightStatusColors.asset.getValue(AssetCondition.DIM),
        )
        assertEquals(
            BadgeColors(Color(0xFFFEE2E2), Color(0xFF991B1B)),
            LightStatusColors.asset.getValue(AssetCondition.OUT),
        )
        assertEquals(
            BadgeColors(Color(0xFFEFEFEF), Color(0xFF555555)),
            LightStatusColors.asset.getValue(AssetCondition.UNKNOWN),
        )
        assertEquals(
            BadgeColors(Color(0xFFEFEFEF), Color(0xFF555555)),
            LightStatusColors.sync.getValue(SyncStatus.QUEUED_OFFLINE),
        )
        assertEquals(
            BadgeColors(Color(0xFFE8EEF7), Color(0xFF1F3864)),
            LightStatusColors.sync.getValue(SyncStatus.QUEUED_ONLINE),
        )
        assertEquals(
            BadgeColors(Color(0xFFFFF3DC), Color(0xFF8A5A00)),
            LightStatusColors.sync.getValue(SyncStatus.SYNCING),
        )
        assertEquals(
            BadgeColors(Color(0xFFFBE4E4), Color(0xFF9B2C2C)),
            LightStatusColors.sync.getValue(SyncStatus.FAILED),
        )
        assertEquals(
            BadgeColors(Color(0xFFFDE8D0), Color(0xFF8A3B00)),
            LightStatusColors.sync.getValue(SyncStatus.CONFLICT),
        )
        assertEquals(
            BadgeColors(Color(0xFFE3F6F1), Color(0xFF1E6B5C)),
            LightStatusColors.sync.getValue(SyncStatus.DONE),
        )
    }

    @Test
    fun `night field asset palette keeps the v2 dark pairs`() {
        assertEquals(
            BadgeColors(Color(0xFF123D34), Color(0xFF8CE3D1)),
            NightFieldStatusColors.asset.getValue(AssetCondition.NORMAL),
        )
        assertEquals(
            BadgeColors(Color(0xFF4A3310), Color(0xFFF7C66D)),
            NightFieldStatusColors.asset.getValue(AssetCondition.DIM),
        )
        assertEquals(
            BadgeColors(Color(0xFF4A1717), Color(0xFFFF9A9A)),
            NightFieldStatusColors.asset.getValue(AssetCondition.OUT),
        )
        assertEquals(
            BadgeColors(Color(0xFF303030), Color(0xFFD0D0D0)),
            NightFieldStatusColors.asset.getValue(AssetCondition.UNKNOWN),
        )
    }

    @Test
    fun `task kind uses a neutral pair that is not borrowed from a status`() {
        val statusPairs =
            LightStatusColors.workOrder.values + LightStatusColors.sync.values + LightStatusColors.asset.values
        assertTrue(LightStatusColors.kind !in statusPairs)
    }
}
