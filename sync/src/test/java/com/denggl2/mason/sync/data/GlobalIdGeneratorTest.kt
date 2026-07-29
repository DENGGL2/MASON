package com.denggl2.mason.sync.data

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalIdGeneratorTest {
    @Test
    fun generatesUuidV7WithRfcVariant() {
        val first = UUID.fromString(GlobalIdGenerator.newId(1_000))
        val second = UUID.fromString(GlobalIdGenerator.newId(1_001))

        assertEquals(7, first.version())
        assertEquals(2, first.variant())
        assertNotEquals(first, second)
        assertTrue(first.toString() < second.toString())
    }
}
