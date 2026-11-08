package com.lain.notes

import android.content.Context
import androidx.compose.ui.graphics.Color

class Pal(
    val name: String,
    val bg: Color,
    val fg: Color,
    val dim: Color,
    val accent: Color,
    val line: Color,
    val tint: Color,
    val glow: Color,
    val danger: Color,
)

val PALS: List<Pal> = listOf(
    Pal("TV-BLUE", Color(0xFF03050A), Color(0xFFD6EAFF), Color(0xFF6C8CA8), Color(0xFF5CC8FF), Color(0xFF1B2A3A), Color(0xFF8FD3FF), Color(0xFF2A6BFF), Color(0xFFFF5470)),
    Pal("NAVI-TEAL", Color(0xFF040809), Color(0xFFCADFE2), Color(0xFF6B8A90), Color(0xFF7FC0C9), Color(0xFF1B2D31), Color(0xFF9CC9D0), Color(0xFF2F6670), Color(0xFFFF6A6A)),
    Pal("ID-RED", Color(0xFF12060A), Color(0xFFEADFF2), Color(0xFF8E6F7C), Color(0xFFE0506A), Color(0xFF3A1621), Color(0xFFE8D6F0), Color(0xFF9A1F3E), Color(0xFFFF8A5C)),
    Pal("ZINE-BLUE", Color(0xFF070C2B), Color(0xFFF0F2FF), Color(0xFF8C9AD8), Color(0xFF4F7BFF), Color(0xFF1B2A6B), Color(0xFF86A4FF), Color(0xFF1F3FD0), Color(0xFFFFB84D)),
    Pal("WIRED", Color(0xFF050407), Color(0xFFE6E0F0), Color(0xFF857F94), Color(0xFFB19CD9), Color(0xFF2A2433), Color(0xFFC9B8F0), Color(0xFF5B3FA0), Color(0xFFFF5C7A)),
)

data class Cfg(
    val theme: Int = 0,
    val mode: Int = 0,          // 0 = TV wall, 1 = full ASCII, 2 = off
    val custom: Boolean = false,
    val cols: Int = 150,
    val ramp: Int = 0,
    val contrast: Float = 1.3f,
    val brightness: Float = 0f,
    val gamma: Float = 1.7f,
    val edge: Float = 0.6f,
    val dither: Float = 0f,
    val zoom: Float = 1f,
    val panX: Float = 0.5f,
    val panY: Float = 0.5f,
    val invert: Boolean = false,
    val fit: Boolean = true,
    val dim: Float = 0.45f,
    val scan: Boolean = true,
    val anim: Boolean = true,
    val glitch: Boolean = true,
    val mix: Boolean = true,
    val cables: Boolean = false,
    val code: Boolean = false,
    val hatch: Boolean = false,
) {
    // Everything that changes the ASCII conversion itself.
    fun asciiKey(): String =
        "${mode == 1}|$cols|$ramp|$contrast|$brightness|$gamma|$edge|$dither|$zoom|$panX|$panY|$invert|$fit"

    // Picking a theme also switches on its signature overlay.
    fun withTheme(i: Int): Cfg =
        copy(theme = i, cables = (i == 1), code = (i == 2), hatch = (i == 3))
}

private const val CFG_VER = 3

class CfgStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("lain_cfg", Context.MODE_PRIVATE)

    /** Loads settings; older saves get the new tuned picture defaults once. */
    fun load(): Cfg {
        val c = read()
        if (sp.getInt("ver", 0) >= CFG_VER) return c
        val d = Cfg()
        val m = c.copy(
            custom = false,
            cols = d.cols, ramp = d.ramp, contrast = d.contrast, brightness = d.brightness,
            gamma = d.gamma, edge = d.edge, dither = d.dither, zoom = d.zoom,
            panX = d.panX, panY = d.panY, invert = d.invert, fit = d.fit,
        )
        save(m)
        return m
    }

    private fun read(): Cfg {
        val d = Cfg()
        return Cfg(
            theme = sp.getInt("theme", d.theme).coerceIn(0, PALS.size - 1),
            mode = sp.getInt("mode", d.mode).coerceIn(0, 2),
            custom = sp.getBoolean("custom", d.custom),
            cols = sp.getInt("cols", d.cols).coerceIn(40, 220),
            ramp = sp.getInt("ramp", d.ramp).coerceIn(0, AsciiArt.RAMPS.size - 1),
            contrast = sp.getFloat("contrast", d.contrast).coerceIn(0.4f, 3f),
            brightness = sp.getFloat("brightness", d.brightness).coerceIn(-0.5f, 0.5f),
            gamma = sp.getFloat("gamma", d.gamma).coerceIn(0.3f, 3f),
            edge = sp.getFloat("edge", d.edge).coerceIn(0f, 1f),
            dither = sp.getFloat("dither", d.dither).coerceIn(0f, 1f),
            zoom = sp.getFloat("zoom", d.zoom).coerceIn(1f, 4f),
            panX = sp.getFloat("panX", d.panX).coerceIn(0f, 1f),
            panY = sp.getFloat("panY", d.panY).coerceIn(0f, 1f),
            invert = sp.getBoolean("invert", d.invert),
            fit = sp.getBoolean("fit", d.fit),
            dim = sp.getFloat("dim", d.dim).coerceIn(0f, 0.92f),
            scan = sp.getBoolean("scan", d.scan),
            anim = sp.getBoolean("anim", d.anim),
            glitch = sp.getBoolean("glitch", d.glitch),
            mix = sp.getBoolean("mix", d.mix),
            cables = sp.getBoolean("cables", d.cables),
            code = sp.getBoolean("code", d.code),
            hatch = sp.getBoolean("hatch", d.hatch),
        )
    }

    fun save(c: Cfg) {
        sp.edit()
            .putInt("ver", CFG_VER)
            .putInt("theme", c.theme)
            .putInt("mode", c.mode)
            .putBoolean("custom", c.custom)
            .putInt("cols", c.cols)
            .putInt("ramp", c.ramp)
            .putFloat("contrast", c.contrast)
            .putFloat("brightness", c.brightness)
            .putFloat("gamma", c.gamma)
            .putFloat("edge", c.edge)
            .putFloat("dither", c.dither)
            .putFloat("zoom", c.zoom)
            .putFloat("panX", c.panX)
            .putFloat("panY", c.panY)
            .putBoolean("invert", c.invert)
            .putBoolean("fit", c.fit)
            .putFloat("dim", c.dim)
            .putBoolean("scan", c.scan)
            .putBoolean("anim", c.anim)
            .putBoolean("glitch", c.glitch)
            .putBoolean("mix", c.mix)
            .putBoolean("cables", c.cables)
            .putBoolean("code", c.code)
            .putBoolean("hatch", c.hatch)
            .apply()
    }
}
