package com.karelisio.bulle.island.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Géométrie instantanée des deux bulles, en pixels, dans le repère de la fenêtre. */
class LiquidGeometry(
    val mainCenterX: Float,
    val mainCenterY: Float,
    val mainHalfW: Float,
    val mainHalfH: Float,
    val mainCorner: Float,
    val secCenterX: Float,
    val secCenterY: Float,
    val secRadius: Float,
    val alpha: Float,
)

/**
 * Fond de l'île en « métaballe » (Android 13+) : la bulle principale et la secondaire
 * sont fusionnées par une union douce de champs de distance. Quand la secondaire s'éloigne,
 * un pont liquide s'étire puis se rompt, comme une goutte qui se détache.
 */
val LiquidSupported: Boolean = Build.VERSION.SDK_INT >= 33

private const val LIQUID_AGSL = """
uniform float2 mainCenter;
uniform float2 mainHalf;
uniform float mainCorner;
uniform float2 secCenter;
uniform float secRadius;
uniform float smoothK;
uniform float alpha;

float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

half4 main(float2 p) {
    float d = sdRoundRect(p - mainCenter, mainHalf, mainCorner);
    if (secRadius > 0.5) {
        float d2 = length(p - secCenter) - secRadius;
        d = smin(d, d2, smoothK);
    }
    float a = (1.0 - smoothstep(-0.8, 0.8, d)) * alpha;
    return half4(0.0, 0.0, 0.0, a);
}
"""

@RequiresApi(33)
private class LiquidShader {
    val shader = RuntimeShader(LIQUID_AGSL)
    val brush = ShaderBrush(shader)

    fun DrawScope.draw(g: LiquidGeometry, smoothK: Float) {
        shader.setFloatUniform("mainCenter", g.mainCenterX, g.mainCenterY)
        val half = minOf(g.mainHalfW, g.mainHalfH)
        shader.setFloatUniform("mainHalf", g.mainHalfW, g.mainHalfH)
        shader.setFloatUniform("mainCorner", g.mainCorner.coerceIn(0f, half))
        shader.setFloatUniform("secCenter", g.secCenterX, g.secCenterY)
        shader.setFloatUniform("secRadius", g.secRadius)
        shader.setFloatUniform("smoothK", smoothK)
        shader.setFloatUniform("alpha", g.alpha)
        drawRect(brush)
    }
}

/**
 * Dessine le fond noir liquide des deux bulles. [geometry] est lu dans la phase de dessin :
 * les animations ne provoquent que des redessins, jamais de recomposition.
 */
@Composable
fun LiquidBackground(smoothK: Float, geometry: DrawScope.() -> LiquidGeometry) {
    if (!LiquidSupported) return
    LiquidCanvas(smoothK, geometry)
}

@RequiresApi(33)
@Composable
private fun LiquidCanvas(smoothK: Float, geometry: DrawScope.() -> LiquidGeometry) {
    val liquid = remember { LiquidShader() }
    Canvas(Modifier.fillMaxSize()) {
        with(liquid) { draw(geometry(), smoothK) }
    }
}
