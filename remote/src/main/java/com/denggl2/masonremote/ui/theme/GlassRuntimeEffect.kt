package com.denggl2.masonremote.ui.theme

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
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

    float roundedRectDistance(float2 point) {
        float2 halfSize = size * 0.5;
        float radius = min(cornerRadius, min(halfSize.x, halfSize.y));
        float2 q = abs(point - halfSize) - (halfSize - radius);
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    }

    float2 roundedRectNormal(float2 point) {
        float2 halfSize = size * 0.5;
        float2 local = point - halfSize;
        float radius = min(cornerRadius, min(halfSize.x, halfSize.y));
        float2 q = abs(local) - (halfSize - float2(radius));
        float2 outside = max(q, float2(0.0));
        float outsideLength = length(outside);

        if (outsideLength > 0.001) {
            return sign(local) * outside / outsideLength;
        }
        if (q.x > q.y) {
            return float2(local.x < 0.0 ? -1.0 : 1.0, 0.0);
        }
        return float2(0.0, local.y < 0.0 ? -1.0 : 1.0);
    }

    float2 safeSample(float2 point) {
        return clamp(point, float2(0.5), size - float2(0.5));
    }

    half4 main(float2 point) {
        float distanceToEdge = -roundedRectDistance(point);
        float band = min(edgeWidth, min(size.x, size.y) * 0.44);
        if (distanceToEdge <= 0.0 || distanceToEdge >= band) {
            return content.eval(safeSample(point));
        }

        float u = clamp(distanceToEdge / band, 0.0, 1.0);
        float amount = clamp(strength / max(band * 0.5, 1.0), 0.0, 0.82);
        float compression = 1.0 + 2.25 * amount;
        float u2 = u * u;
        float u3 = u2 * u;
        float warpedU =
            (compression - 1.0) * u3 +
            (2.0 - 2.0 * compression) * u2 +
            compression * u;
        float offset = band * (warpedU - u);
        float2 normal = roundedRectNormal(point);
        float chromaticRatio = clamp(dispersion / max(strength, 0.001), 0.0, 0.08);

        float2 greenPoint = point - normal * offset;
        float2 redPoint = point - normal * offset * (1.0 + chromaticRatio);
        float2 bluePoint = point - normal * offset * (1.0 - chromaticRatio);
        half4 base = content.eval(safeSample(greenPoint));
        half red = content.eval(safeSample(redPoint)).r;
        half blue = content.eval(safeSample(bluePoint)).b;
        half3 refracted = half3(red, base.g, blue);
        return half4(refracted, base.a);
    }
"""

private const val GLASS_REFRACTION_LOG_TAG = "MasonGlass"

internal fun Modifier.glassRefraction(
    enabled: Boolean,
    cornerRadius: Dp,
    strength: Dp = 10.dp,
    dispersion: Dp = 0.5.dp,
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
                    setFloatUniform("edgeWidth", with(density) { 20.dp.toPx() })
                    setFloatUniform("strength", with(density) { strength.toPx() })
                    setFloatUniform("dispersion", with(density) { dispersion.toPx() })
                }.let { shader ->
                    AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
                        .asComposeRenderEffect()
                }
            }.getOrElse { error ->
                shaderFailed.set(true)
                Log.w(GLASS_REFRACTION_LOG_TAG, "Unable to create refraction shader", error)
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
