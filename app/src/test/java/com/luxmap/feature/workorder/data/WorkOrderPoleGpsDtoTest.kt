package com.luxmap.feature.workorder.data

import com.luxmap.feature.workorder.data.dto.CompleteWorkOrderRequestDto
import com.luxmap.feature.workorder.data.dto.PoleLocationRequestDto
import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import com.luxmap.feature.workorder.data.dto.WorkOrderItemDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WorkOrderPoleGpsDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val detailJson =
        """
        {
          "work_order_id": "WO-1",
          "title": "Đo GPS cột",
          "task_kind": "pole_gps_update",
          "wo_status": "assigned",
          "pole_ids": ["P-1", "P-2"],
          "attachments": [
            {
              "attachment_id": "A-1",
              "file_name": "qr.pdf",
              "byte_count": 2048,
              "uploaded_at": "2026-11-13T01:00:00Z",
              "download_url": "/api/v1/work-orders/WO-1/attachments/A-1"
            }
          ],
          "pole_locations": [
            {
              "pole_id": "P-1",
              "lat": 10.5,
              "lng": 106.5,
              "accuracy_m": 4.5,
              "captured_at": "2026-11-13T02:00:00Z"
            }
          ]
        }
        """.trimIndent()

    @Test
    fun `detail parses pole ids attachments and pole locations`() {
        val dto = json.decodeFromString<WorkOrderDetailDto>(detailJson)
        assertEquals(listOf("P-1", "P-2"), dto.poleIds)
        assertEquals("/api/v1/work-orders/WO-1/attachments/A-1", dto.attachments.single().downloadUrl)
        assertEquals(2048L, dto.attachments.single().byteCount)
        assertEquals(4.5, dto.poleLocations.single().accuracyM, 0.0)
    }

    @Test
    fun `detail without the new fields defaults to empty lists`() {
        val dto =
            json.decodeFromString<WorkOrderDetailDto>(
                """{"work_order_id":"WO-2","title":"t","task_kind":"repair","wo_status":"open"}""",
            )
        assertTrue(dto.poleIds.isEmpty())
        assertTrue(dto.attachments.isEmpty())
        assertTrue(dto.poleLocations.isEmpty())
    }

    @Test
    fun `detail maps to domain with parsed instants`() {
        val detail = json.decodeFromString<WorkOrderDetailDto>(detailJson).toWorkOrderDetail()
        assertEquals(listOf("P-1", "P-2"), detail.poleIds)
        assertEquals(Instant.parse("2026-11-13T01:00:00Z"), detail.attachments.single().uploadedAt)
        assertEquals(Instant.parse("2026-11-13T02:00:00Z"), detail.poleLocations.single().capturedAt)
    }

    @Test
    fun `complete request omits pole_locations when null and encodes them when present`() {
        val without = Json { encodeDefaults = false }.encodeToString(CompleteWorkOrderRequestDto(reportNote = "x"))
        assertTrue(!without.contains("pole_locations"))
        val with =
            Json.encodeToString(
                CompleteWorkOrderRequestDto(
                    reportNote = "x",
                    poleLocations = listOf(PoleLocationRequestDto("P-1", 10.5, 106.5, 4.5, "2026-11-13T02:00:00Z")),
                ),
            )
        assertTrue(with.contains("\"pole_id\":\"P-1\""))
        assertTrue(with.contains("\"accuracy_m\":4.5"))
        assertTrue(with.contains("\"captured_at\":\"2026-11-13T02:00:00Z\""))
    }

    @Test
    fun `pole list item with null position and null location parses`() {
        val dto =
            json.decodeFromString<com.luxmap.feature.survey.data.dto.PoleListItemDto>(
                """{"pole_id":"P-9","position":null,"location":null,"fixture_status":"unknown"}""",
            )
        assertNull(dto.position)
        assertNull(dto.location)
    }

    @Test
    fun `work order item parses pole ids`() {
        val dto =
            json.decodeFromString<WorkOrderItemDto>(
                """
                {"work_order_id":"WO-1","title":"t","commune_id":"C","task_kind":"pole_gps_update",
                 "case_id":"WO-1","fault_ids":[],"wo_status":"assigned",
                 "created_at":"2026-11-13T00:00:00Z","updated_at":"2026-11-13T00:00:00Z",
                 "pole_ids":["P-1"]}
                """.trimIndent(),
            )
        assertEquals(listOf("P-1"), dto.poleIds)
    }
}
