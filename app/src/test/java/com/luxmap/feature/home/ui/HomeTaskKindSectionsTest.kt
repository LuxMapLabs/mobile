package com.luxmap.feature.home.ui

import com.luxmap.core.theme.WorkOrderTaskKind
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeTaskKindSectionsTest {
    @Test
    fun `section order leaves out pole GPS update - that task kind lives in the Survey Hub now`() {
        assertFalse(TASK_KIND_SECTION_ORDER.contains(WorkOrderTaskKind.POLE_GPS_UPDATE))
    }
}
