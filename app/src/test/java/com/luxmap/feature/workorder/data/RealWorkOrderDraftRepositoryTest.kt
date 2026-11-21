package com.luxmap.feature.workorder.data

import com.luxmap.core.security.TokenStore
import com.luxmap.feature.workorder.data.dao.WorkOrderDraftDao
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderDraftEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class RealWorkOrderDraftRepositoryTest {
    private val dao = mockk<WorkOrderDraftDao>(relaxed = true)
    private val tokens = mockk<TokenStore>()
    private val repository = RealWorkOrderDraftRepository(dao, tokens)

    @Test
    fun `draft fields round trip as owner scoped Room data`() =
        runTest {
            val draft = WorkOrderDraft("Đã kiểm tra", "LED", mapOf("F" to "fault_present"))
            var row: LocalWorkOrderDraftEntity? = null
            coEvery { tokens.cachedUserId() } returns "U"
            coEvery { dao.save(any()) } coAnswers { row = firstArg() }
            coEvery { dao.get("U", "W") } coAnswers { row }
            assertEquals("U", repository.ownerUserId())
            assertNull(repository.load("U", "W"))
            repository.save("U", "W", draft)
            assertEquals("U", row?.ownerUserId)
            assertEquals("W", row?.workOrderId)
            assertEquals(draft, repository.load("U", "W"))
            repository.delete("U", "W")
            coVerify(exactly = 1) { dao.delete("U", "W") }
        }

    @Test
    fun `session owner changes prevent reading saving or deleting another users draft`() =
        runTest {
            coEvery { tokens.cachedUserId() } returns "NEW"
            val operations: List<suspend () -> Unit> =
                listOf(
                    {
                        repository.load("OLD", "W")
                        Unit
                    },
                    { repository.save("OLD", "W", WorkOrderDraft("note", "", emptyMap())) },
                    { repository.delete("OLD", "W") },
                )
            for (operation in operations) {
                try {
                    operation()
                    fail("Expected owner check to reject changed session")
                } catch (_: IllegalStateException) {
                    // Expected: no DAO operation is allowed for a different signed-in user.
                }
            }
            coVerify(exactly = 0) { dao.get(any(), any()) }
            coVerify(exactly = 0) { dao.save(any()) }
            coVerify(exactly = 0) { dao.delete(any(), any()) }
        }
}
