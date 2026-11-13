package com.luxmap.feature.survey.ui.polephoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PolePhotoReviewLogicTest {
    @Test
    fun `confidence at or above 85 percent is high`() {
        assertEquals(ConfidenceBand.HIGH, confidenceBand(0.85))
        assertEquals(ConfidenceBand.HIGH, confidenceBand(0.99))
    }

    @Test
    fun `confidence from 60 up to 85 percent is medium`() {
        assertEquals(ConfidenceBand.MEDIUM, confidenceBand(0.60))
        assertEquals(ConfidenceBand.MEDIUM, confidenceBand(0.849))
    }

    @Test
    fun `confidence below 60 percent or missing is low`() {
        assertEquals(ConfidenceBand.LOW, confidenceBand(0.599))
        assertEquals(ConfidenceBand.LOW, confidenceBand(null))
    }

    @Test
    fun `confidence label puts the risk wording before the number`() {
        assertEquals("Tin cậy cao · 92%", confidenceLabel(0.92))
        assertEquals("Tin cậy vừa · 73%", confidenceLabel(0.73))
        assertEquals("Tin cậy thấp · 48%", confidenceLabel(0.48))
    }

    @Test
    fun `confidence label is null when the model gave no confidence`() {
        assertNull(confidenceLabel(null))
    }

    @Test
    fun `review value wins over the ai value for the effective label`() {
        assertEquals("off", effectiveCvState(cvState = "on", reviewedCvState = "off"))
        assertEquals("on", effectiveCvState(cvState = "on", reviewedCvState = null))
    }

    @Test
    fun `effective label is null when ai was not conclusive and nobody reviewed`() {
        assertNull(effectiveCvState(cvState = "inconclusive", reviewedCvState = null))
        assertNull(effectiveCvState(cvState = null, reviewedCvState = null))
    }

    @Test
    fun `reviewer choice is the effective label even when ai failed outright`() {
        assertEquals("on", effectiveCvState(cvState = null, reviewedCvState = "on"))
        assertEquals("off", effectiveCvState(cvState = "inconclusive", reviewedCvState = "off"))
    }

    @Test
    fun `source line says ai judged when no review exists`() {
        assertEquals("AI nhận định", verdictSourceText(cvState = "on", reviewedCvState = null))
    }

    @Test
    fun `source line names the ai verdict the engineer overrode`() {
        assertEquals(
            "Bạn đã sửa · AI nhận định Đèn tắt",
            verdictSourceText(cvState = "off", reviewedCvState = "on"),
        )
    }

    @Test
    fun `source line says agreed when review matches ai`() {
        assertEquals("Bạn đã xác nhận cùng AI", verdictSourceText(cvState = "on", reviewedCvState = "on"))
    }

    @Test
    fun `source line asks for a choice when ai failed`() {
        assertEquals(
            "AI không xử lý được · cần bạn chọn nhãn",
            verdictSourceText(cvState = null, reviewedCvState = null),
        )
    }

    @Test
    fun `source line asks for a choice when ai was inconclusive`() {
        assertEquals(
            "AI không rõ ràng · cần bạn chọn nhãn",
            verdictSourceText(cvState = "inconclusive", reviewedCvState = null),
        )
    }

    @Test
    fun `source line after overriding an inconclusive ai does not claim ai judged`() {
        assertEquals(
            "Bạn đã chọn nhãn · AI không kết luận",
            verdictSourceText(cvState = "inconclusive", reviewedCvState = "on"),
        )
    }

    @Test
    fun `action label agrees with ai when selection matches`() {
        assertEquals("Đồng ý với AI", reviewActionLabel(selected = "on", aiCvState = "on"))
    }

    @Test
    fun `action label saves a correction when selection differs`() {
        assertEquals("Lưu nhãn đã sửa", reviewActionLabel(selected = "off", aiCvState = "on"))
        assertEquals("Lưu nhãn đã sửa", reviewActionLabel(selected = "on", aiCvState = "inconclusive"))
        assertEquals("Lưu nhãn đã sửa", reviewActionLabel(selected = "on", aiCvState = null))
    }
}
