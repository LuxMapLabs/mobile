package com.luxmap.feature.home.ui

import com.luxmap.core.theme.WorkOrderTaskKind
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTaskKindSectionsTest {
    @Test
    fun `section order includes pole GPS update so those orders are not dropped`() {
        assertTrue(TASK_KIND_SECTION_ORDER.contains(WorkOrderTaskKind.POLE_GPS_UPDATE))
    }
}
