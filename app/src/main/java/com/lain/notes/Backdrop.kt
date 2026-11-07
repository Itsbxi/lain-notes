package com.lain.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A CRT television. Coordinates are fractions of the canvas. ch: 0 picture, 1 rain, 2 fire, 3 plasma, 4 static. */
class TvSpec(val x: Float, val y: Float, val w: Float, val h: Float, val pan: Float, val ch: Int)

val TV_WALL: List<TvSpec> = listOf(
    TvSpec(-0.02f, 0.012f, 0.50f, 0.240f, 0f, 0),
    TvSpec(0.56f, 0.040f, 0.42f, 0.210f, 0f, 1),
    TvSpec(-0.02f, 0.310f, 0.62f, 0.290f, 0.17f, 0),
    TvSpec(0.645f, 0.330f, 0.355f, 0.150f, 0f, 3),
    TvSpec(0.70f, 0.500f, 0.24f, 0.098f, 0f, 4),
    TvSpec(-0.02f, 0.640f, 0.58f, 0.305f, 0.16f, 0),
    TvSpec(0.62f, 0.640f, 0.38f, 0.190f, 0f, 2),
    TvSpec(0.72f, 0.852f, 0.22f, 0.092f, 0f, 0),
)

val TV_BOOT: List<TvSpec> = listOf(
    TvSpec(0.06f, 0.05f, 0.88f, 0.42f, 0.18f, 0),
)

private val SHELVES = floatArrayOf(0.272f, 0.612f, 0.955f)

private fun chTint(pal: Pal, ch: Int, mix: Boolean): Color {
    if (!mix) return pal.tint
    return when (ch) {
        1 -> pal.accent
        2 -> pal.danger
        3 -> pal.accent.copy(alpha = 0.9f)
        else -> pal.tint
    }
}

/**
 * The animated background: a wall of CRT TVs showing ASCII (mode 0),
 * the picture as full-screen ASCII (mode 1) or nothing (mode 2).
 */
@Composable
fun Backdrop(
    cfg: Cfg,
    pal: Pal,
    live: Live,
    noise: ImageBitmap?,
    tick: () -> Int,
    active: Boolean,
    modifier: Modifier = Modifier,
    tvs: List<TvSpec> = TV_WALL,
    shelves: Boolean = true,
    forceMode: Int = -1,
) {
    val mode = if (forceMode >= 0) forceMode else cfg.mode
    Canvas(modifier) {
        drawRect(pal.bg)
        // reading these inside the draw scope makes the canvas repaint on every new frame
        @Suppress("UNUSED_VARIABLE")
        val frameVersion = live.ver
        val t = if (active) tick() else 0
        if (mode == 0) {
            drawTvWall(pal, cfg, live, noise, t, tvs, shelves)
        } else if (mode == 1) {
            drawFull(pal, cfg, live, t)
        }
    }
}

private fun DrawScope.drawTvWall(
    pal: Pal,
    cfg: Cfg,
    live: Live,
    noise: ImageBitmap?,
    t: Int,
    tvs: List<TvSpec>,
    shelves: Boolean,
) {
    val unit = size.width / 540f
    if (shelves) {
        for (sy in SHELVES) {
            drawRect(Color(0xFF606C78).copy(alpha = 0.55f), Offset(0f, sy * size.height), Size(size.width, 0.012f * size.height))
            drawRect(Color.Black.copy(alpha = 0.5f), Offset(0f, sy * size.height + 0.012f * size.height), Size(size.width, 0.008f * size.height))
        }
    }
    for (i in tvs.indices) {
        drawTv(tvs[i], i, pal, cfg, live, noise, t, unit)
    }
}

private fun DrawScope.drawTv(
    s: TvSpec,
    idx: Int,
    pal: Pal,
    cfg: Cfg,
    live: Live,
    noise: ImageBitmap?,
    t: Int,
    unit: Float,
) {
    val x = s.x * size.width
    val y = s.y * size.height
    val w = s.w * size.width
    val h = s.h * size.height
    val bodyR = CornerRadius(8f * unit, 8f * unit)

    // cabinet
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF1E2228), Color(0xFF0B0D10)), startY = y, endY = y + h),
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = bodyR,
    )
    drawRoundRect(
        color = Color(0xFF38404A),
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = bodyR,
        style = Stroke(width = 1.2f * unit),
    )

    // screen rectangle
    val sx0 = x + 0.05f * w
    val sy0 = y + 0.09f * h
    val sx1 = x + (0.95f - s.pan) * w
    val sy1 = y + 0.91f * h
    val sw = sx1 - sx0
    val sh = sy1 - sy0
    if (sw < 8f || sh < 8f) return
    val rad = 0.16f * min(sw, sh)
    val bz = 4f * unit
    drawRoundRect(
        color = Color(0xFF07080B),
        topLeft = Offset(sx0 - bz, sy0 - bz),
        size = Size(sw + 2f * bz, sh + 2f * bz),
        cornerRadius = CornerRadius(rad + bz, rad + bz),
    )
    drawRoundRect(
        color = Color(0xFF3C444E),
        topLeft = Offset(sx0 - bz, sy0 - bz),
        size = Size(sw + 2f * bz, sh + 2f * bz),
        cornerRadius = CornerRadius(rad + bz, rad + bz),
        style = Stroke(width = 1.2f * unit),
    )

    // which program is this TV showing?
    var ch = s.ch
    if (!cfg.mix && ch in 1..3) ch = 0
    val img: ImageBitmap? = if (ch in 0..3) live.image(ch) else null
    val flick = if (t == 0) 1f else 0.86f + 0.14f * sin((t + idx * 9) * 0.33f)
    val burst = ch == 4 || img == null || (t > 0 && ((t / 12 + idx * 5) % 19 == 0))
    val tint = chTint(pal, ch, cfg.mix)
    val dstW = max(1, sw.toInt())
    val dstH = max(1, sh.toInt())

    val screen = Path()
    screen.addRoundRect(RoundRect(Rect(sx0, sy0, sx1, sy1), CornerRadius(rad, rad)))
    clipPath(screen) {
        drawRect(Color(0xFF02060C), Offset(sx0, sy0), Size(sw, sh))

        if (!burst && img != null) {
            val iw = img.width
            val ih = img.height
            val ar = sw / sh
            val wide = iw.toFloat() / ih > ar
            val baseW = if (wide) ih * ar else iw.toFloat()
            val baseH = if (wide) ih.toFloat() else iw / ar
            var zoomV = 1f
            var px = 0.5f
            var py = 0.4f
            if (ch == 0) {
                zoomV = 0.70f + 0.30f * (((idx * 37) % 10) / 10f)
                px = 0.5f + 0.25f * sin(idx * 1.7f)
                py = 0.30f + 0.25f * sin(idx * 2.3f + 1f)
            }
            val cropW = baseW * zoomV
            val cropH = baseH * zoomV
            val ox = ((iw - cropW) * px.coerceIn(0f, 1f)).coerceAtLeast(0f)
            val oy = ((ih - cropH) * py.coerceIn(0f, 1f)).coerceAtLeast(0f)
            val srcSize = IntSize(max(1, cropW.toInt()), max(1, cropH.toInt()))
            val srcOff = IntOffset(ox.toInt(), oy.toInt())
            val tear = cfg.glitch && t > 0 && ((t / 3 + idx) % 23 == 0)
            val jit = if (tear) ((t % 7) - 3) * 3f * unit else 0f
            drawImage(
                image = img,
                srcOffset = srcOff,
                srcSize = srcSize,
                dstOffset = IntOffset((sx0 + jit).toInt(), sy0.toInt()),
                dstSize = IntSize(dstW, dstH),
                alpha = flick,
                colorFilter = ColorFilter.tint(tint),
            )
            if (tear) {
                drawImage(
                    image = img,
                    srcOffset = srcOff,
                    srcSize = srcSize,
                    dstOffset = IntOffset((sx0 - jit * 2f).toInt(), sy0.toInt()),
                    dstSize = IntSize(dstW, dstH),
                    alpha = 0.35f,
                    colorFilter = ColorFilter.tint(pal.danger),
                )
            }
        } else if (noise != null) {
            val nw = noise.width
            val srcSz = max(8, nw / 2)
            val span = max(1, nw - srcSz)
            drawImage(
                image = noise,
                srcOffset = IntOffset((t * 37 + idx * 101) % span, (t * 53 + idx * 29) % span),
                srcSize = IntSize(srcSz, srcSz),
                dstOffset = IntOffset(sx0.toInt(), sy0.toInt()),
                dstSize = IntSize(dstW, dstH),
                alpha = 0.6f,
                colorFilter = ColorFilter.tint(pal.tint, BlendMode.Modulate),
            )
        }

        val c = Offset((sx0 + sx1) / 2f, (sy0 + sy1) / 2f)
        val r = max(sw, sh)
        // phosphor glow
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(pal.glow.copy(alpha = 0.50f), Color.Transparent),
                center = c,
                radius = r * 0.70f,
            ),
            topLeft = Offset(sx0, sy0),
            size = Size(sw, sh),
        )
        // tube curvature (dark corners)
        drawRect(
            brush = Brush.radialGradient(
                0.0f to Color.Transparent,
                0.55f to Color.Transparent,
                1.0f to Color.Black.copy(alpha = 0.85f),
                center = c,
                radius = r * 0.72f,
            ),
            topLeft = Offset(sx0, sy0),
            size = Size(sw, sh),
        )
        // rolling refresh bar
        val barY = sy0 + ((t * 3 + idx * 47) % (dstH + 80)) - 40f
        drawRect(pal.tint.copy(alpha = 0.07f), Offset(sx0, barY), Size(sw, 22f * unit))
        // glass glare
        val glare = Path()
        glare.moveTo(sx0 + sw * 0.08f, sy0 + sh * 0.10f)
        glare.lineTo(sx0 + sw * 0.40f, sy0 + sh * 0.06f)
        glare.lineTo(sx0 + sw * 0.20f, sy0 + sh * 0.30f)
        glare.lineTo(sx0 + sw * 0.06f, sy0 + sh * 0.34f)
        glare.close()
        drawPath(glare, Color.White.copy(alpha = 0.08f))
    }

    // control panel on the bigger sets
    if (s.pan > 0f) {
        val px0 = sx1 + bz + 6f * unit
        val px1 = x + w - 8f * unit
        if (px1 - px0 > 6f) {
            drawRect(Color(0xFF0E1014), Offset(px0, sy0), Size(px1 - px0, sh))
            for (k in 0 until 7) {
                val ly = sy0 + (8f + k * 6f) * unit
                drawLine(Color(0xFF1E232A), Offset(px0 + 4f * unit, ly), Offset(px1 - 4f * unit, ly), 1.2f * unit)
            }
            for (k in 0 until 3) {
                drawCircle(Color(0xFF262B33), 6f * unit, Offset((px0 + px1) / 2f, sy0 + sh * 0.55f + k * sh * 0.12f))
            }
        }
    }
}

private fun DrawScope.drawFull(pal: Pal, cfg: Cfg, live: Live, t: Int) {
    val img = live.image(0) ?: return
    val dw = max(1, size.width.toInt())
    val dh = max(1, size.height.toInt())
    val flick = if (t == 0) 1f else 0.9f + 0.1f * sin(t * 0.3f)
    drawImage(
        image = img,
        dstSize = IntSize(dw, dh),
        alpha = 0.9f * flick,
        colorFilter = ColorFilter.tint(pal.tint),
    )
    if (cfg.glitch && t > 0 && (t % 90) < 4) {
        val bh = img.height
        for (k in 0 until 3) {
            val sy = ((t * 131 + k * 977) % max(1, bh - 60)).coerceAtLeast(0)
            val sh = 18 + k * 10
            val dx = ((t * 17 + k * 53) % 48) - 24
            val dy = (sy.toFloat() / bh * dh).toInt()
            drawImage(
                image = img,
                srcOffset = IntOffset(0, sy),
                srcSize = IntSize(img.width, sh),
                dstOffset = IntOffset(dx, dy),
                dstSize = IntSize(dw, max(1, (sh.toFloat() / bh * dh).toInt())),
                alpha = 0.85f,
                colorFilter = ColorFilter.tint(pal.accent),
            )
        }
    }
}

/** Layer under the UI: hatch lines, tangled cables and the code overlay. */
@Composable
fun UnderLayer(cfg: Cfg, pal: Pal) {
    if (cfg.hatch) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 7f * density
            var x = -size.height
            while (x < size.width) {
                drawLine(pal.tint.copy(alpha = 0.10f), Offset(x, size.height), Offset(x + size.height, 0f), 1.2f * density)
                x += step
            }
        }
    }
    if (cfg.cables) {
        Canvas(Modifier.fillMaxSize()) {
            val rnd = java.util.Random(11)
            for (k in 0 until 16) {
                val x0 = size.width * rnd.nextFloat()
                val x1 = size.width * rnd.nextFloat()
                val y1 = size.height * (0.35f + 0.65f * rnd.nextFloat())
                val sag = size.height * (0.10f + 0.25f * rnd.nextFloat())
                val c1 = (rnd.nextFloat() - 0.5f) * size.width * 0.6f
                val c2 = (rnd.nextFloat() - 0.5f) * size.width * 0.6f
                val wdt = (2f + 4f * rnd.nextFloat()) * density
                val p = Path()
                p.moveTo(x0, -10f)
                p.cubicTo(x0 + c1, sag, x1 + c2, y1 + sag * 0.6f, x1, y1)
                drawPath(p, Color.Black.copy(alpha = 0.78f), style = Stroke(width = wdt, cap = StrokeCap.Round))
                drawPath(p, pal.tint.copy(alpha = 0.06f), style = Stroke(width = wdt * 0.25f, cap = StrokeCap.Round))
            }
        }
    }
    if (cfg.code) {
        Box(Modifier.fillMaxSize().padding(8.dp)) {
            Text(
                AsciiArt.CODE,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    color = pal.tint.copy(alpha = 0.22f),
                ),
                softWrap = false,
            )
        }
    }
}

/** Layer above the UI: scanlines, vignette, a slow scan beam and rare glitch bars. */
@Composable
fun TopLayer(cfg: Cfg, pal: Pal, tick: () -> Int, active: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        if (cfg.scan) {
            val step = 3.5f * density
            var y = 0f
            while (y < size.height) {
                drawRect(Color.Black.copy(alpha = 0.20f), Offset(0f, y), Size(size.width, step * 0.45f))
                y += step
            }
        }
        drawRect(
            brush = Brush.radialGradient(
                0.0f to Color.Transparent,
                0.6f to Color.Transparent,
                1.0f to Color.Black.copy(alpha = 0.70f),
                center = center,
                radius = max(size.width, size.height) * 0.75f,
            ),
        )
    }
    if (cfg.anim && active) {
        Canvas(Modifier.fillMaxSize()) {
            val t = tick()
            val travel = (size.height + 600f).toInt()
            val y = ((t * 7) % max(1, travel)) - 300f
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, pal.tint.copy(alpha = 0.07f), Color.Transparent),
                    startY = y,
                    endY = y + 260f,
                ),
                topLeft = Offset(0f, y),
                size = Size(size.width, 260f),
            )
            if (cfg.glitch && (t % 140) in 0..3) {
                for (k in 0 until 3) {
                    val r = (((t / 2) * 7919 + k * 104729) % 1000) / 1000f
                    drawRect(
                        pal.accent.copy(alpha = 0.10f),
                        Offset(0f, size.height * r),
                        Size(size.width, (3f + k * 2f) * density),
                    )
                }
            }
        }
    }
}
