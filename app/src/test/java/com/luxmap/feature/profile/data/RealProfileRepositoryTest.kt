package com.luxmap.feature.profile.data

import com.luxmap.core.common.communeLabel
import com.luxmap.core.network.UserApi
import com.luxmap.feature.auth.data.CommuneSummaryDto
import com.luxmap.feature.auth.data.CurrentUserResponseDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RealProfileRepositoryTest {
    @Test
    fun `maps available commune names and keeps ids without names`() =
        runTest {
            val api = mockk<UserApi>()
            coEvery { api.me() } returns
                CurrentUserResponseDto(
                    userId = "U1",
                    username = "crew",
                    email = "crew@example.com",
                    fullName = "Nguyễn Văn Bình",
                    role = "field_engineer",
                    communeIds = listOf("COM-001", "COM-002"),
                    communes = listOf(CommuneSummaryDto("COM-001", "Phước Hiệp")),
                )
            val profile = RealProfileRepository(api).fetchCurrentUser().getOrThrow()
            assertEquals(
                listOf("Phước Hiệp", "COM-002"),
                profile.communeIds.map { communeLabel(it, profile.communeNames[it]) },
            )
        }
}
