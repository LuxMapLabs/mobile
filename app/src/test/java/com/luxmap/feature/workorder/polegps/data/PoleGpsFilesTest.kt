package com.luxmap.feature.workorder.polegps.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PoleGpsFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `server ids cannot escape the root`() {
        val files = PoleGpsFiles(tmp.root)
        val file = files.fileFor("../WO", "../../a")
        assertTrue(file.canonicalPath.startsWith(tmp.root.canonicalPath))
        assertEquals("______a.pdf", file.name)
    }

    @Test
    fun `deleteOrder removes the whole order folder`() {
        val files = PoleGpsFiles(tmp.root)
        val file = files.fileFor("WO-1", "A-1")
        file.parentFile!!.mkdirs()
        file.writeText("x")
        files.deleteOrder("WO-1")
        assertFalse(file.exists())
    }
}
