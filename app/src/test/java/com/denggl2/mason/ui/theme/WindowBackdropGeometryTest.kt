package com.denggl2.mason.ui.theme

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindowBackdropGeometryTest {
    @Test
    fun `sample inside window fills the requested destination`() {
        val geometry = calculateWindowBackdropSampleGeometry(
            windowPosition = IntOffset(20, 30),
            layerSize = IntSize(40, 60),
            windowSize = IntSize(100, 200),
            imageSize = IntSize(50, 100),
            bleedPixels = 10,
        )

        assertEquals(
            WindowBackdropSampleGeometry(
                sourceOffset = IntOffset(5, 10),
                sourceSize = IntSize(20, 30),
                destinationOffset = IntOffset.Zero,
                destinationSize = IntSize(40, 60),
            ),
            geometry,
        )
    }

    @Test
    fun `top left clamp preserves destination alignment instead of stretching`() {
        val geometry = calculateWindowBackdropSampleGeometry(
            windowPosition = IntOffset.Zero,
            layerSize = IntSize(40, 60),
            windowSize = IntSize(100, 200),
            imageSize = IntSize(50, 100),
            bleedPixels = 10,
        )

        assertEquals(
            WindowBackdropSampleGeometry(
                sourceOffset = IntOffset.Zero,
                sourceSize = IntSize(15, 25),
                destinationOffset = IntOffset(10, 10),
                destinationSize = IntSize(30, 50),
            ),
            geometry,
        )
    }

    @Test
    fun `bottom right clamp reduces destination with the source`() {
        val geometry = calculateWindowBackdropSampleGeometry(
            windowPosition = IntOffset(80, 180),
            layerSize = IntSize(40, 60),
            windowSize = IntSize(100, 200),
            imageSize = IntSize(50, 100),
            bleedPixels = 10,
        )

        assertEquals(
            WindowBackdropSampleGeometry(
                sourceOffset = IntOffset(35, 85),
                sourceSize = IntSize(15, 15),
                destinationOffset = IntOffset.Zero,
                destinationSize = IntSize(30, 30),
            ),
            geometry,
        )
    }

    @Test
    fun `sample fully outside window is omitted`() {
        assertNull(
            calculateWindowBackdropSampleGeometry(
                windowPosition = IntOffset(140, 250),
                layerSize = IntSize(40, 60),
                windowSize = IntSize(100, 200),
                imageSize = IntSize(50, 100),
                bleedPixels = 10,
            ),
        )
    }
}
