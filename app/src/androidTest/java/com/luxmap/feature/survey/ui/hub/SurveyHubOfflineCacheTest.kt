package com.luxmap.feature.survey.ui.hub

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.luxmap.core.database.AppDatabase
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.survey.data.entity.LocalAssignedPoleGpsOrderEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SurveyHubOfflineCacheTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun reopened_gps_cache_is_visible_with_stale_notice_when_survey_is_unavailable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "hub-offline-cache-test.db"
        context.deleteDatabase(name)
        val cache = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        runBlocking {
            cache.poleGpsOrderCacheDao().replaceAll(
                "A",
                listOf(LocalAssignedPoleGpsOrderEntity("A", "GPS-1", "GPS đã lưu", "C", "assigned", null, null)),
            )
        }
        cache.close()
        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        val rows = runBlocking { reopened.poleGpsOrderCacheDao().all("A") }
        reopened.close()
        context.deleteDatabase(name)
        var opened: String? = null
        rule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state =
                        SurveyHubUiState.Success(
                            assignedRoutes = emptyList(),
                            assignedPoleGpsOrders =
                                rows.map {
                                    AssignedSurveyRoute(
                                        it.workOrderId,
                                        it.title,
                                        it.communeId,
                                        it.woStatus,
                                        it.dueDate,
                                        it.scheduledDate,
                                    )
                                },
                            isStale = true,
                        ),
                    onOpenWorkOrderDetail = { opened = it },
                )
            }
        }
        rule.onNodeWithText("GPS đã lưu").assertIsDisplayed()
        rule.onNodeWithText("Danh sách có thể chưa đầy đủ", substring = true).assertIsDisplayed()
        rule.onNodeWithText("GPS đã lưu").performClick()
        rule.runOnIdle { assertEquals("GPS-1", opened) }
    }

    @Test
    fun offline_without_cache_shows_download_guidance() {
        rule.setContent { LuxMapTheme { SurveyHubScreen(state = SurveyHubUiState.OfflineEmpty) } }
        rule.onNodeWithText("Chưa có dữ liệu đã lưu").assertIsDisplayed()
        rule.onNodeWithText("Kết nối mạng để tải danh sách.", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Chưa có việc khảo sát được giao").assertDoesNotExist()
    }
}
