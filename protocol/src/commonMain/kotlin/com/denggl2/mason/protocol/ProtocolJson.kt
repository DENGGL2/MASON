package com.denggl2.mason.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object MasonProtocolJson {
    val format: Json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    inline fun <reified T> encode(value: T): String = format.encodeToString(value)

    inline fun <reified T> decode(value: String): T = format.decodeFromString(value)
}
