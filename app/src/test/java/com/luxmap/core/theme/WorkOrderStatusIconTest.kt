package com.luxmap.core.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Verified
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkOrderStatusIconTest {
    @Test
    fun `assigned and open share the assignment icon`() {
        assertEquals(Icons.Filled.Assignment, WorkOrderStatus.ASSIGNED.icon())
        assertEquals(Icons.Filled.Assignment, WorkOrderStatus.OPEN.icon())
    }

    @Test
    fun `in progress uses the autorenew icon`() {
        assertEquals(Icons.Filled.Autorenew, WorkOrderStatus.IN_PROGRESS.icon())
    }

    @Test
    fun `done uses check circle, verified uses verified, cancelled uses cancel`() {
        assertEquals(Icons.Filled.CheckCircle, WorkOrderStatus.DONE.icon())
        assertEquals(Icons.Filled.Verified, WorkOrderStatus.VERIFIED.icon())
        assertEquals(Icons.Filled.Cancel, WorkOrderStatus.CANCELLED.icon())
    }
}
