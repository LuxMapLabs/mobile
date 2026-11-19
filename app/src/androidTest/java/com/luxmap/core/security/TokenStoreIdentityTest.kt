package com.luxmap.core.security

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TokenStoreIdentityTest {
    @Test
    fun refresh_preserves_identity_and_clear_removes_it() =
        runTest {
            val store = TokenStore(ApplicationProvider.getApplicationContext(), TokenCipher())
            store.clear()
            try {
                store.saveSession("access", "refresh", 3600, true, "A")
                val sessionA = store.identitySessionId()!!
                assertTrue(store.saveCachedUserIdForSession("user-A", sessionA))
                store.saveSession("access-2", "refresh-2", 3600)
                assertEquals("user-A", store.cachedUserId())
                assertEquals(sessionA, store.identitySessionId())
                store.saveSession("access-B", "refresh-B", 3600, true, "B")
                assertNull(store.cachedUserId())
                assertFalse(store.saveCachedUserIdForSession("user-A", sessionA))
                assertNull(store.cachedUserId())
                store.saveCachedUserId("user-B")
                store.clear()
                assertNull(store.cachedUserId())
            } finally {
                store.clear()
            }
        }
}
