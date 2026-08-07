package com.denggl2.mason.ui.theme

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean

private const val GLASS_REFRACTION_SHADER = """
    uniform shader content;
    uniform float2 size;
    uniform float cornerRadius;
    uniform float edgeWidth;
    uniform float strength;
    uniform float dispersion;
    uniform float saturation;

    float roundedRectDistance(float2 point) {
        float2 halfSize = size * 0.5;
        float2 q = abs(point - halfSize) - (halfSize - cornerRadius);
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - cornerRadius;
    }

    float2 safeSample(float2 point) {
        return clamp(point, float2(0.5), size - float2(0.5));
    }

    half4 main(float2 point) {
        float distanceToEdge = -roundedRectDistance(point);
        float edge = 1.0 - smoothstep(0.0, edgeWidth, distanceToEdge);
        edge = edge * edge;
        float2 center = size * 0.5;
        float2 direction = normalize((point - center) + float2(0.001));
        float2 offset = direction * strength * edge;

        half4 base = content.eval(safeSample(point - offset));
        half red = content.eval(safeSample(point - offset - direction * dispersion * edge)).r;
        half blue = content.eval(safeSample(point - offset + direction * dispersion * edge)).b;
        half3 refracted = half3(red, base.g, blue);
        half luminance = dot(refracted, half3(0.2126, 0.7152, 0.0722));
        half3 saturated = mix(half3(luminance), refracted, half(saturation));
        return half4(saturated, base.a);
    }
"""

internal fun Modifier.glassRefraction(
    enabled: Boolean,
    cornerRadius: Dp,
    strength: Dp = 1.25.dp,
    dispersion: Dp = 0.55.dp,
): Modifier = composed {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return@composed this
    }
    val density = LocalDensity.current
    val shaderFailed = remember { AtomicBoolean(false) }
    var layerSize by remember { mutableStateOf(IntSize.Zero) }
    val renderEffect = remember(
        layerSize,
        density.density,
        cornerRadius,
        strength,
        dispersion,
    ) {
        if (layerSize == IntSize.Zero || shaderFailed.get()) {
            null
        } else {
            runCatching {
                RuntimeShader(GLASS_REFRACTION_SHADER).apply {
                    setFloatUniform("size", layerSize.width.toFloat(), layerSize.height.toFloat())
                    setFloatUniform("cornerRadius", with(density) { cornerRadius.toPx() })
                    setFloatUniform("edgeWidth", with(density) { 14.dp.toPx() })
                    setFloatUniform("strength", with(density) { strength.toPx() })
                    setFloatUniform("dispersion", with(density) { dispersion.toPx() })
                    setFloatUniform("saturation", 1.18f)
                }.let { shader ->
                    AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
                        .asComposeRenderEffect()
                }
            }.getOrElse {
                shaderFailed.set(true)
                null
            }
        }
    }
    this
        .onSizeChanged { layerSize = it }
        .graphicsLayer {
            this.renderEffect = renderEffect
        }
}
