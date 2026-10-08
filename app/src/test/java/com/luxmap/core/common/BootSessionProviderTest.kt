package com.luxmap.core.common

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BootSessionProviderTest {
    private class FakeStore {
        var savedBootCount: Int? = null
        var savedUuid: String? = null
    }

    private class InMemoryBootSessionProvider(
        private val store: FakeStore,
        private val bootCount: Int,
    ) {
        suspend fun currentBootSessionId(): String {
            if (store.savedBootCount == bootCount && store.savedUuid != null) return store.savedUuid!!
            val newUuid = java.util.UUID.randomUUID().toString()
            store.savedBootCount = bootCount
            store.savedUuid = newUuid
            return newUuid
        }
    }

    @Test
    fun `same boot count returns the same uuid`() =
        runTest {
            val store = FakeStore()
            val provider = InMemoryBootSessionProvider(store, bootCount = 5)
            val first = provider.currentBootSessionId()
            val second = provider.currentBootSessionId()
            assertEquals(first, second)
        }

    @Test
    fun `boot count change produces a new uuid`() =
        runTest {
            val store = FakeStore()
            val before = InMemoryBootSessionProvider(store, bootCount = 5).currentBootSessionId()
            val after = InMemoryBootSessionProvider(store, bootCount = 6).currentBootSessionId()
            assertNotEquals(before, after)
        }
}
