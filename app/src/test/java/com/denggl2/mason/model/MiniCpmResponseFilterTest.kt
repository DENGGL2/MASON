package com.denggl2.mason.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiniCpmResponseFilterTest {
    @Test
    fun dropsReasoningWhenOnlyTheClosingTagIsGenerated() {
        val filter = MiniCpmResponseFilter()

        assertNull(filter.consume("reasoning"))
        assertNull(filter.consume(" continues"))
        assertEquals("answer", filter.consume("</think>answer"))
        assertEquals(" more", filter.consume(" more"))
        assertNull(filter.finish())
    }

    @Test
    fun retainsAnAnswerWhenNoReasoningClosingTagIsGenerated() {
        val filter = MiniCpmResponseFilter()

        assertNull(filter.consume("direct"))
        assertNull(filter.consume(" answer"))
        assertEquals("direct answer", filter.finish())
    }
}
