package com.luxmap.core.common

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeBootSessionStore : BootSessionStore {
    private var savedBootCount: Int? = null
    private var savedUuid: String? = null

    override suspend fun read(): Pair<Int?, String?> = savedBootCount to savedUuid

    override suspend fun write(
        bootCount: Int,
        uuid: String,
    ) {
        savedBootCount = bootCount
        savedUuid = uuid
    }
}

class BootSessionProviderTest {
    @Test
    fun `same boot count keeps the saved uuid`() {
        val existing = resolveExistingBootSessionId(currentBootCount = 5, savedBootCount = 5, savedUuid = "uuid-5")
        assertEquals("uuid-5", existing)
    }

    @Test
    fun `boot count change returns null, caller must mint a new uuid`() {
        val existing = resolveExistingBootSessionId(currentBootCount = 6, savedBootCount = 5, savedUuid = "uuid-5")
        assertNull(existing)
    }

    @Test
    fun `no saved state yet returns null`() {
        val existing = resolveExistingBootSessionId(currentBootCount = 5, savedBootCount = null, savedUuid = null)
        assertNull(existing)
    }

    @Test
    fun `unreadable BOOT_COUNT always returns null, even if a saved uuid exists`() {
        // Reusing the same uuid forever on a device that cannot read BOOT_COUNT would be worse
        // than losing reboot detection on it - a real reboot would then go undetected forever.
        val existing =
            resolveExistingBootSessionId(currentBootCount = null, savedBootCount = null, savedUuid = "stale-uuid")
        assertNull(existing)
    }

    @Test
    fun `currentBootSessionId returns the same uuid across two calls at the same boot count`() =
        runTest {
            val store = FakeBootSessionStore()
            val provider = RealBootSessionProvider(store, bootCountReader = { 5 })

            val first = provider.currentBootSessionId()
            val second = provider.currentBootSessionId()

            assertEquals(first, second)
        }

    @Test
    fun `currentBootSessionId mints a new uuid when the boot count changes`() =
        runTest {
            val store = FakeBootSessionStore()
            val before = RealBootSessionProvider(store, bootCountReader = { 5 }).currentBootSessionId()
            val after = RealBootSessionProvider(store, bootCountReader = { 6 }).currentBootSessionId()

            assertNotEquals(before, after)
        }

    @Test
    fun `currentBootSessionId mints a new uuid every call when BOOT_COUNT cannot be read`() =
        runTest {
            val store = FakeBootSessionStore()
            val provider = RealBootSessionProvider(store, bootCountReader = { null })

            val first = provider.currentBootSessionId()
            val second = provider.currentBootSessionId()

            assertNotEquals(first, second)
        }
}
