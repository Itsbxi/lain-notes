package com.lain.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.Typeface
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One grayscale frame (0..255 per pixel). */
class Plane(val w: Int, val h: Int, val lum: ByteArray)

/** One or more frames; a still picture is a clip with a single frame. */
class Clip(val frames: List<Plane>, val fps: Int)

object Engine {

    /** Height / width of one monospace character cell. */
    const val CELL = 1.94f
    const val MAX_FRAMES = 90

    private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    // ------------------------------------------------------------------ decoding

    fun planeOf(b: Bitmap): Plane {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        val out = ByteArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            val a = (c ushr 24) and 255
            val r = (c shr 16) and 255
            val g = (c shr 8) and 255
            val bl = c and 255
            val l = (r * 54 + g * 183 + bl * 19) shr 8
            out[i] = ((l * a) / 255).toByte()
        }
        return Plane(w, h, out)
    }

    private fun fit(b: Bitmap, maxSide: Int): Bitmap {
        val m = max(b.width, b.height)
        if (m <= maxSide) return b
        val s = maxSide.toFloat() / m
        val nw = max(1, (b.width * s).roundToInt())
        val nh = max(1, (b.height * s).roundToInt())
        return Bitmap.createScaledBitmap(b, nw, nh, true)
    }

    fun decodeStill(ctx: Context, uri: Uri, maxSide: Int): Plane? {
        try {
            val cr = ctx.contentResolver
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            var bmp: Bitmap = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            var deg = 0
            try {
                cr.openInputStream(uri)?.use { s ->
                    val ex = ExifInterface(s)
                    val o = ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    deg = when (o) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                }
            } catch (e: Throwable) {
                deg = 0
            }
            if (deg != 0) {
                val m = Matrix()
                m.postRotate(deg.toFloat())
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            return planeOf(fit(bmp, maxSide))
        } catch (e: Throwable) {
            return null
        }
    }

    fun decodeAsset(ctx: Context, path: String): Plane? {
        return try {
            val bmp = ctx.assets.open(path).use { s -> BitmapFactory.decodeStream(s) }
            if (bmp == null) null else planeOf(fit(bmp, 1100))
        } catch (e: Throwable) {
            null
        }
    }

    fun loadDefault(ctx: Context): Clip? {
        val p = decodeAsset(ctx, "scenes/portrait.png") ?: return null
        return Clip(listOf(p), 1)
    }

    /** Animated GIF -> frames at 10 fps (first 9 seconds). */
    fun decodeGif(ctx: Context, uri: Uri): Clip? {
        try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val mv = Movie.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val mw = mv.width()
            val mh = mv.height()
            if (mw <= 0 || mh <= 0) return null
            val dur = mv.duration()
            val scale = min(1f, 320f / max(mw, mh))
            val tw = max(1, (mw * scale).roundToInt())
            val th = max(1, (mh * scale).roundToInt())
            val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val frames = ArrayList<Plane>()
            val n = if (dur <= 0) 1 else min(MAX_FRAMES, max(1, dur / 100))
            for (i in 0 until n) {
                bmp.eraseColor(Color.BLACK)
                cv.save()
                cv.scale(scale, scale)
                mv.setTime(if (dur <= 0) 0 else i * 100)
                mv.draw(cv, 0f, 0f)
                cv.restore()
                frames.add(planeOf(bmp))
            }
            return Clip(frames, if (n > 1) 10 else 1)
        } catch (e: Throwable) {
            return null
        }
    }

    /** Video -> frames at 10 fps (first 9 seconds). */
    fun decodeVideo(ctx: Context, uri: Uri, onProgress: (Int, Int) -> Unit): Clip? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            var vw = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var vh = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) {
                val tmp = vw
                vw = vh
                vh = tmp
            }
            if (durMs <= 0L || vw <= 0 || vh <= 0) return null
            val n = min(MAX_FRAMES, max(1, (durMs / 100L).toInt()))
            val scale = min(1f, 320f / max(vw, vh))
            val tw = max(1, (vw * scale).roundToInt())
            val th = max(1, (vh * scale).roundToInt())
            val frames = ArrayList<Plane>()
            for (i in 0 until n) {
                val tUs = i * 100000L
                var bmp: Bitmap? = if (Build.VERSION.SDK_INT >= 27) {
                    r.getScaledFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST, tw, th)
                } else {
                    r.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST)
                }
                if (bmp != null) {
                    frames.add(planeOf(fit(bmp, 320)))
                    bmp = null
                }
                onProgress(i + 1, n)
            }
            if (frames.isEmpty()) return null
            return Clip(frames, 10)
        } catch (e: Throwable) {
            return null
        } finally {
            try {
                r.release()
            } catch (e: Throwable) {
                // ignore
            }
        }
    }

    // --------------------------------------------------------------- persistence

    private const val MAGIC = 0x4C41494E

    fun saveClip(ctx: Context, c: Clip) {
        val f = File(ctx.filesDir, "src.bin")
        DataOutputStream(BufferedOutputStream(FileOutputStream(f))).use { o ->
            o.writeInt(MAGIC)
            o.writeInt(c.fps)
            o.writeInt(c.frames.size)
            for (p in c.frames) {
                o.writeInt(p.w)
                o.writeInt(p.h)
                o.write(p.lum)
            }
        }
    }

    fun loadClip(ctx: Context): Clip? {
        return try {
            val f = File(ctx.filesDir, "src.bin")
            if (!f.exists()) {
                null
            } else {
                DataInputStream(BufferedInputStream(FileInputStream(f))).use { i ->
                    if (i.readInt() != MAGIC) {
                        null
                    } else {
                        val fps = i.readInt().coerceIn(1, 30)
                        val n = i.readInt()
                        if (n < 1 || n > 200) {
                            null
                        } else {
                            val frames = ArrayList<Plane>()
                            for (k in 0 until n) {
                                val w = i.readInt()
                                val h = i.readInt()
                                if (w < 1 || h < 1 || w > 4096 || h > 4096) return null
                                val buf = ByteArray(w * h)
                                i.readFully(buf)
                                frames.add(Plane(w, h, buf))
                            }
                            Clip(frames, fps)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            null
        }
    }

    // ------------------------------------------------------------ ASCII conversion

    private fun sampleGrid(p: Plane, cfg: Cfg, cols: Int, rows: Int, ta: Float): FloatArray {
        val w = p.w
        val h = p.h
        val imgAspect = w.toFloat() / h.toFloat()
        val wide = imgAspect > ta
        val coverW = if (wide) h * ta else w.toFloat()
        val coverH = if (wide) h.toFloat() else w / ta
        val containW = if (wide) w.toFloat() else h * ta
        val containH = if (wide) w / ta else h.toFloat()
        val baseW = if (cfg.fit) containW else coverW
        val baseH = if (cfg.fit) containH else coverH
        val zoom = cfg.zoom.coerceIn(1f, 4f)
        val rw = baseW / zoom
        val rh = baseH / zoom
        val left = (w - rw) * cfg.panX.coerceIn(0f, 1f)
        val top = (h - rh) * cfg.panY.coerceIn(0f, 1f)
        val cw = rw / cols
        val chh = rh / rows
        val out = FloatArray(cols * rows)
        val lum = p.lum
        for (cy in 0 until rows) {
            val y0 = top + cy * chh
            val iy0 = floor(y0).toInt()
            val iy1 = max(iy0 + 1, ceil(y0 + chh).toInt())
            val ya = max(iy0, 0)
            val yb = min(iy1, h)
            for (cx in 0 until cols) {
                val x0 = left + cx * cw
                val ix0 = floor(x0).toInt()
                val ix1 = max(ix0 + 1, ceil(x0 + cw).toInt())
                val xa = max(ix0, 0)
                val xb = min(ix1, w)
                var sum = 0
                var yy = ya
                while (yy < yb) {
                    var xx = xa
                    val rb = yy * w
                    while (xx < xb) {
                        sum += lum[rb + xx].toInt() and 255
                        xx++
                    }
                    yy++
                }
                val area = (iy1 - iy0) * (ix1 - ix0)
                out[cy * cols + cx] = if (area > 0) sum.toFloat() / (area * 255f) else 0f
            }
        }
        return out
    }

    /**
     * In "fit" mode the picture does not cover the whole screen. Instead of black bars the
     * empty cells get a soft, dim, blurred copy of the same picture, so the layout fills the phone.
     */
    private fun fillBars(p: Plane, cfg: Cfg, g: FloatArray, cols: Int, rows: Int, ta: Float) {
        val w = p.w
        val h = p.h
        val imgAspect = w.toFloat() / h.toFloat()
        val wide = imgAspect > ta
        val containW = if (wide) w.toFloat() else h * ta
        val containH = if (wide) w / ta else h.toFloat()
        val zoom = cfg.zoom.coerceIn(1f, 4f)
        val rw = containW / zoom
        val rh = containH / zoom
        val left = (w - rw) * cfg.panX.coerceIn(0f, 1f)
        val top = (h - rh) * cfg.panY.coerceIn(0f, 1f)
        val cw = rw / cols
        val chh = rh / rows
        val sc = 4
        val gc = cols / sc + 2
        val gr = rows / sc + 2
        val coarse = sampleGrid(p, cfg.copy(fit = false, zoom = 1f), gc, gr, ta)
        var cmax = 0.05f
        for (cv in coarse) {
            if (cv > cmax) cmax = cv
        }
        val gain = 0.8f / cmax
        for (cy in 0 until rows) {
            val sy = top + (cy + 0.5f) * chh
            val yIn = sy >= 0f && sy < h
            for (cx in 0 until cols) {
                val sx = left + (cx + 0.5f) * cw
                if (yIn && sx >= 0f && sx < w) continue
                val fx = cx.toFloat() * gc / cols
                val fy = cy.toFloat() * gr / rows
                val x0 = min(fx.toInt(), gc - 1)
                val y0 = min(fy.toInt(), gr - 1)
                val x1 = min(x0 + 1, gc - 1)
                val y1 = min(y0 + 1, gr - 1)
                val tx = fx - x0
                val ty = fy - y0
                val upper = coarse[y0 * gc + x0] * (1f - tx) + coarse[y0 * gc + x1] * tx
                val lower = coarse[y1 * gc + x0] * (1f - tx) + coarse[y1 * gc + x1] * tx
                g[cy * cols + cx] = (upper * (1f - ty) + lower * ty) * gain
            }
        }
    }

    private fun percentiles(v: FloatArray): FloatArray {
        val s = v.copyOf()
        s.sort()
        val n = s.size
        val lo = s[(n * 0.02f).toInt().coerceIn(0, n - 1)]
        val hi = s[(n * 0.98f).toInt().coerceIn(0, n - 1)]
        return floatArrayOf(lo, hi)
    }

    private fun finish(v: FloatArray, cols: Int, rows: Int, cfg: Cfg, lo: Float, hi: Float): List<String> {
        val n = v.size
        val span = hi - lo
        val gamma = cfg.gamma.coerceIn(0.3f, 3f)
        val t = FloatArray(n)
        for (i in 0 until n) {
            var x = v[i]
            if (span > 0.03f) x = (x - lo) / span
            x = x.coerceIn(0f, 1f).pow(gamma)
            x = (x - 0.5f) * cfg.contrast + 0.5f + cfg.brightness
            t[i] = x.coerceIn(0f, 1f)
        }
        var o = t
        if (cfg.edge > 0.01f) {
            o = FloatArray(n)
            for (cy in 0 until rows) {
                val ym = max(cy - 1, 0)
                val yp = min(cy + 1, rows - 1)
                for (cx in 0 until cols) {
                    val xm = max(cx - 1, 0)
                    val xp = min(cx + 1, cols - 1)
                    val a = t[ym * cols + xm]
                    val b = t[ym * cols + cx]
                    val c = t[ym * cols + xp]
                    val d = t[cy * cols + xm]
                    val f = t[cy * cols + xp]
                    val g = t[yp * cols + xm]
                    val hh = t[yp * cols + cx]
                    val ii = t[yp * cols + xp]
                    val gx = (c + 2f * f + ii) - (a + 2f * d + g)
                    val gy = (g + 2f * hh + ii) - (a + 2f * b + c)
                    val mag = sqrt(gx * gx + gy * gy) / 4f
                    o[cy * cols + cx] = (t[cy * cols + cx] + cfg.edge * min(1f, mag * 2f)).coerceIn(0f, 1f)
                }
            }
        }
        val ramp = AsciiArt.RAMPS[cfg.ramp.coerceIn(0, AsciiArt.RAMPS.size - 1)]
        val rn = ramp.length
        val lines = ArrayList<String>(rows)
        val sb = StringBuilder(cols)
        for (cy in 0 until rows) {
            sb.setLength(0)
            for (cx in 0 until cols) {
                var x = o[cy * cols + cx]
                if (cfg.invert) x = 1f - x
                val gated = x < 0.06f
                if (cfg.dither > 0.01f) {
                    val th = BAYER[(cy and 3) * 4 + (cx and 3)] / 16f - 0.5f
                    x += th * cfg.dither * 2f / (rn - 1)
                }
                val idx = if (gated) 0 else (x * (rn - 1)).roundToInt().coerceIn(0, rn - 1)
                sb.append(ramp[idx])
            }
            lines.add(sb.toString())
        }
        return lines
    }

    /** Converts every frame of a clip; levels are shared so video does not pump. */
    fun toLinesAll(clip: Clip, cfg: Cfg, aspect: Float): List<List<String>> {
        val cols = cfg.cols.coerceIn(40, 220)
        val ta = aspect.coerceIn(0.2f, 5f)
        val rows = ((cols / ta) / CELL).roundToInt().coerceIn(8, 400)
        val grids = ArrayList<FloatArray>(clip.frames.size)
        var lo = 0f
        var hi = 0f
        for (p in clip.frames) {
            val g = sampleGrid(p, cfg, cols, rows, ta)
            if (cfg.fit) fillBars(p, cfg, g, cols, rows, ta)
            grids.add(g)
            val pr = percentiles(g)
            lo += pr[0]
            hi += pr[1]
        }
        val k = max(1, grids.size)
        lo /= k
        hi /= k
        val out = ArrayList<List<String>>(grids.size)
        for (g in grids) out.add(finish(g, cols, rows, cfg, lo, hi))
        return out
    }

    // ------------------------------------------------------------------- effects

    private fun hash(a: Int, b: Int, c: Int): Int {
        var h = a * 374761393 + b * 668265263 + c * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }

    /**
     * "Glyph cast": glyphs start as scrambled noise and resolve into the picture
     * as p goes from 0 to 1 (top of the picture resolves first).
     */
    fun cast(lines: List<String>, p: Float, seed: Int, tick: Int): List<String> {
        val rows = lines.size
        val out = ArrayList<String>(rows)
        val sb = StringBuilder()
        val gl = AsciiArt.GLYPHS
        for (y in 0 until rows) {
            val s = lines[y]
            sb.setLength(0)
            for (x in 0 until s.length) {
                val ch = s[x]
                if (ch == ' ') {
                    sb.append(' ')
                } else {
                    val r = (hash(x, y, seed) and 0xFFFF) / 65536f
                    val th = r * 0.65f + (y.toFloat() / rows) * 0.35f
                    if (p >= th) {
                        sb.append(ch)
                    } else {
                        val gi = (hash(x, y, tick + seed * 31) and 0x7FFFFFFF) % gl.length
                        sb.append(gl[gi])
                    }
                }
            }
            out.add(sb.toString())
        }
        return out
    }

    // ----------------------------------------------------------------- rendering

    fun newPaint(): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        p.typeface = Typeface.MONOSPACE
        p.color = Color.WHITE
        return p
    }

    fun heightFor(cols: Int, rows: Int, wPx: Int): Int {
        val cw = wPx.toFloat() / max(1, cols)
        return max(1, (rows * cw * CELL).roundToInt())
    }

    /** Draws white glyphs on a transparent bitmap; tinting happens at draw time. */
    fun renderInto(bmp: Bitmap, lines: List<String>, paint: Paint) {
        bmp.eraseColor(0)
        if (lines.isEmpty()) return
        val cols = lines[0].length
        if (cols == 0) return
        val rows = lines.size
        val cw = bmp.width.toFloat() / cols
        val ch = bmp.height.toFloat() / rows
        paint.textSize = 100f
        val m = paint.measureText("M")
        if (m <= 0f) return
        paint.textSize = 100f * cw / m
        val fm = paint.fontMetrics
        val base = (ch - (fm.descent - fm.ascent)) / 2f - fm.ascent
        val cv = Canvas(bmp)
        for (r in 0 until rows) {
            val s = lines[r]
            if (s.isBlank()) continue
            cv.drawText(s, 0, s.length, 0f, r * ch + base, paint)
        }
    }

    /** Grayscale random noise tile used for TV static. */
    fun noise(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val px = IntArray(size * size)
        val rnd = java.util.Random(42)
        for (i in px.indices) {
            val g = rnd.nextInt(256)
            px[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        bmp.setPixels(px, 0, size, 0, 0, size, size)
        return bmp
    }
}
