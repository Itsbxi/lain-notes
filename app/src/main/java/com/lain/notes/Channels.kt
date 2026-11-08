package com.lain.notes

import android.graphics.Bitmap
import android.graphics.Paint
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** A double-buffered ASCII bitmap: the renderer writes one buffer while the UI draws the other. */
class Chan(val wPx: Int, val hPx: Int) {
    private val bufs: Array<Bitmap> = arrayOf(
        Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888),
        Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888),
    )
    private val imgs: Array<ImageBitmap> = arrayOf(bufs[0].asImageBitmap(), bufs[1].asImageBitmap())

    @Volatile
    private var front = 0

    fun image(): ImageBitmap = imgs[front]

    fun draw(lines: List<String>, paint: Paint) {
        val back = 1 - front
        Engine.renderInto(bufs[back], lines, paint)
        front = back
    }
}

/** Falling binary rain. */
class RainGen(val cols: Int, val rows: Int) {
    private val rnd = java.util.Random(5)
    private val head = IntArray(cols) { -rnd.nextInt(rows * 2) }
    private val spd = IntArray(cols) { 1 + rnd.nextInt(2) }
    private val len = IntArray(cols) { 5 + rnd.nextInt(max(2, rows / 2)) }

    fun step(): List<String> {
        for (x in 0 until cols) {
            head[x] += spd[x]
            if (head[x] - len[x] > rows) {
                head[x] = -rnd.nextInt(rows)
                spd[x] = 1 + rnd.nextInt(2)
                len[x] = 5 + rnd.nextInt(max(2, rows / 2))
            }
        }
        val out = ArrayList<String>(rows)
        val sb = StringBuilder(cols)
        for (y in 0 until rows) {
            sb.setLength(0)
            for (x in 0 until cols) {
                val d = head[x] - y
                if (d < 0 || d > len[x]) {
                    sb.append(' ')
                } else if (d == 0) {
                    sb.append('@')
                } else {
                    val k = 1f - d.toFloat() / len[x]
                    if (k > 0.35f) {
                        sb.append(if (rnd.nextBoolean()) '1' else '0')
                    } else if (k > 0.15f) {
                        sb.append(':')
                    } else {
                        sb.append('.')
                    }
                }
            }
            out.add(sb.toString())
        }
        return out
    }
}

/** Classic "doom fire" in ASCII. */
class FireGen(val cols: Int, val rows: Int) {
    private val rnd = java.util.Random(9)
    private val heat = IntArray(cols * rows)
    private val maxHeat = 15
    private val ramp = " .:-=+*#%@"

    fun step(): List<String> {
        for (x in 0 until cols) {
            heat[(rows - 1) * cols + x] = if (rnd.nextInt(10) < 8) maxHeat else maxHeat - 5
        }
        for (y in 1 until rows) {
            for (x in 0 until cols) {
                val src = y * cols + x
                val p = heat[src]
                if (p == 0) {
                    heat[src - cols] = 0
                } else {
                    val r = rnd.nextInt(3)
                    val dst = (src - r + 1).coerceIn(cols, rows * cols - 1)
                    val extra = if (rnd.nextInt(4) == 0) 1 else 0
                    heat[dst - cols] = max(0, p - (r and 1) - extra)
                }
            }
        }
        val out = ArrayList<String>(rows)
        val sb = StringBuilder(cols)
        val n = ramp.length
        for (y in 0 until rows) {
            sb.setLength(0)
            for (x in 0 until cols) {
                val idx = (heat[y * cols + x] * (n - 1) / maxHeat).coerceIn(0, n - 1)
                sb.append(ramp[idx])
            }
            out.add(sb.toString())
        }
        return out
    }
}

/** Smooth moving plasma. */
class PlasmaGen(val cols: Int, val rows: Int) {
    private val ramp = " .,:;-~=+*#%@"

    fun step(t: Int): List<String> {
        val tt = t * 0.12f
        val n = ramp.length
        val out = ArrayList<String>(rows)
        val sb = StringBuilder(cols)
        val cx = cols / 2f
        val cy = rows / 2f
        for (y in 0 until rows) {
            sb.setLength(0)
            for (x in 0 until cols) {
                val fx = x * 0.22f
                val fy = y * 0.40f
                val dx = x - cx
                val dy = y - cy
                var v = sin(fx + tt) + sin(fy - tt * 0.8f) + sin((fx + fy) * 0.6f + tt * 0.5f) +
                    sin(sqrt(dx * dx * 0.05f + dy * dy * 0.2f) - tt)
                v = (v + 4f) / 8f
                val idx = (v * (n - 1)).toInt().coerceIn(0, n - 1)
                sb.append(ramp[idx])
            }
            out.add(sb.toString())
        }
        return out
    }
}

/**
 * Owns the animated ASCII "channels" that the TV screens display:
 * 0 = picture (still, GIF or video), 1 = rain, 2 = fire, 3 = plasma.
 * [run] renders about ten frames per second on a background thread.
 */
class Live {
    @Volatile
    var pic: List<List<String>> = emptyList()

    @Volatile
    var picVer: Int = 0

    @Volatile
    var picW: Int = 720

    /** Exact bitmap height in pixels (0 = derive it from the grid). */
    @Volatile
    var picH: Int = 0

    /** Size of the settings preview in pixels (0 = no preview wanted). */
    @Volatile
    var prevW: Int = 0

    @Volatile
    var prevH: Int = 0

    @Volatile
    var fps: Int = 10

    @Volatile
    var castFrom: Long = 0L

    @Volatile
    var on: Boolean = true

    @Volatile
    var mix: Boolean = true

    /** Bumped after every rendered frame so draw scopes know to repaint. */
    var ver: Int by mutableStateOf(0)

    private val chans: Array<Chan?> = arrayOfNulls<Chan>(5)
    private var seenVer = -1
    private var wasCasting = false

    fun image(k: Int): ImageBitmap? {
        if (k < 0 || k >= chans.size) return null
        val c = chans[k]
        return if (c == null) null else c.image()
    }

    private fun chanFor(k: Int, w: Int, h: Int): Chan {
        val c = chans[k]
        if (c != null && c.wPx == w && c.hPx == h) return c
        val nc = Chan(w, h)
        chans[k] = nc
        return nc
    }

    private fun renderPic(paint: Paint, t: Int): Boolean {
        val frames = pic
        if (frames.isEmpty()) return false
        val first = frames[0]
        if (first.isEmpty() || first[0].isEmpty()) return false
        val cols = first[0].length
        val rows = first.size
        val w = picW
        val h = if (picH > 0) picH else Engine.heightFor(cols, rows, w)
        val existing = chans[0]
        if (existing == null || existing.wPx != w || existing.hPx != h) seenVer = -1
        val chan = chanFor(0, w, h)
        val now = SystemClock.uptimeMillis()
        val casting = on && castFrom > 0L && (now - castFrom) < CAST_MS
        val animated = on && frames.size > 1
        val changed = seenVer != picVer
        if (!(casting || animated || changed || wasCasting)) return false
        wasCasting = casting
        seenVer = picVer
        var idx = 0
        if (animated) idx = ((now / (1000L / max(1, fps))) % frames.size).toInt()
        var lines = frames[idx.coerceIn(0, frames.size - 1)]
        if (casting) lines = Engine.cast(lines, (now - castFrom).toFloat() / CAST_MS, 7, t)
        chan.draw(lines, paint)
        return true
    }

    /** Draws the text straight at preview size, so the preview is smooth instead of a shrunk bitmap. */
    private fun renderPrev(paint: Paint): Boolean {
        val w = prevW
        val h = prevH
        if (w < 16 || h < 16) return false
        val frames = pic
        if (frames.isEmpty()) return false
        val now = SystemClock.uptimeMillis()
        var idx = 0
        if (on && frames.size > 1) idx = ((now / (1000L / max(1, fps))) % frames.size).toInt()
        chanFor(4, w, h).draw(frames[idx.coerceIn(0, frames.size - 1)], paint)
        return true
    }

    private fun renderGen(k: Int, lines: List<String>, paint: Paint) {
        if (lines.isEmpty()) return
        val w = 480
        val h = Engine.heightFor(lines[0].length, lines.size, w)
        chanFor(k, w, h).draw(lines, paint)
    }

    suspend fun run() {
        val paint = Engine.newPaint()
        val rain = RainGen(64, 36)
        val fire = FireGen(64, 36)
        val plasma = PlasmaGen(64, 36)
        var t = 0
        while (true) {
            val t0 = SystemClock.uptimeMillis()
            var drew = false
            try {
                if (renderPic(paint, t)) drew = true
                if (renderPrev(paint)) drew = true
                if (mix) {
                    // when animation is off, each generator still draws one frame so the screens are not blank
                    if (on || chans[1] == null) {
                        renderGen(1, rain.step(), paint)
                        drew = true
                    }
                    if (on || chans[2] == null) {
                        renderGen(2, fire.step(), paint)
                        drew = true
                    }
                    if (on || chans[3] == null) {
                        renderGen(3, plasma.step(t), paint)
                        drew = true
                    }
                }
            } catch (e: Throwable) {
                // a failed frame must never kill the loop
            }
            if (drew) ver += 1
            t += 1
            val used = SystemClock.uptimeMillis() - t0
            delay(if (on) max(20L, 100L - used) else 250L)
        }
    }

    companion object {
        const val CAST_MS = 1900L
    }
}
