package com.denggl2.mason.sync.data

import java.security.SecureRandom
import java.util.UUID

object GlobalIdGenerator {
    private val random = SecureRandom()

    @Synchronized
    fun newId(nowMillis: Long = System.currentTimeMillis()): String {
        require(nowMillis >= 0) { "UUIDv7 timestamp cannot be negative" }
        val timestamp = nowMillis and 0x0000ffffffffffffL
        val randomA = random.nextInt(1 shl 12).toLong()
        val randomB = random.nextLong() and 0x3fffffffffffffffL
        val mostSignificantBits = (timestamp shl 16) or 0x7000L or randomA
        val leastSignificantBits = 0x8000000000000000UL.toLong() or randomB
        return UUID(mostSignificantBits, leastSignificantBits).toString()
    }
}
