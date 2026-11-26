package com.luxmap.core.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Verified
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkOrderStatusIconTest {
    @Test
    fun `assigned and open share the assignment icon`() {
        assertEquals(Icons.Rounded.Assignment, WorkOrderStatus.ASSIGNED.icon())
        assertEquals(Icons.Rounded.Assignment, WorkOrderStatus.OPEN.icon())
    }

    @Test
    fun `in progress uses the autorenew icon`() {
        assertEquals(Icons.Rounded.Autorenew, WorkOrderStatus.IN_PROGRESS.icon())
    }

    @Test
    fun `done uses check circle, verified uses verified, cancelled uses cancel`() {
        assertEquals(Icons.Rounded.CheckCircle, WorkOrderStatus.DONE.icon())
        assertEquals(Icons.Rounded.Verified, WorkOrderStatus.VERIFIED.icon())
        assertEquals(Icons.Rounded.Cancel, WorkOrderStatus.CANCELLED.icon())
    }
}
