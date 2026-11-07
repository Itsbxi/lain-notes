package com.lain.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ------------------------------------------------------------------------ boot

@Composable
fun BootScreen(
    cfg: Cfg,
    pal: Pal,
    live: Live,
    noise: ImageBitmap?,
    tick: () -> Int,
    onDone: () -> Unit,
) {
    val lines = AsciiArt.BOOT_LINES
    val total = lines.sumOf { it.length } + lines.size
    var typed by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (typed < total) {
            delay(18)
            typed += 1
        }
        delay(900)
        onDone()
    }

    val logoRows = AsciiArt.LOGO.split("\n")
    val castP = (typed / (total * 0.6f)).coerceIn(0f, 1f)
    val logo = Engine.cast(logoRows, castP, 3, typed)

    Box(
        Modifier
            .fillMaxSize()
            .background(pal.bg)
            .clickable { onDone() }
    ) {
        Backdrop(
            cfg = cfg,
            pal = pal,
            live = live,
            noise = noise,
            tick = tick,
            active = true,
            modifier = Modifier.fillMaxSize(),
            tvs = TV_BOOT,
            shelves = false,
            forceMode = 0,
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp)
        ) {
            Text(logo.joinToString("\n"), style = mono(12, pal.accent, true), softWrap = false)
            Spacer(Modifier.height(10.dp))
            var shown = typed
            for (line in lines) {
                val take = shown.coerceIn(0, line.length)
                shown -= line.length + 1
                val cursor = if (take > 0 && take < line.length) "_" else ""
                Text(line.take(take) + cursor, style = mono(12, pal.fg))
            }
            Spacer(Modifier.height(6.dp))
            Text("[ tap to skip ]", style = mono(10, pal.dim))
        }
    }
}

// ------------------------------------------------------------------------ list

@Composable
fun Header(pal: Pal, count: Int, onSettings: () -> Unit) {
    val clock by produceState(initialValue = "") {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        while (true) {
            value = fmt.format(Date())
            delay(1000)
        }
    }
    val ticker by produceState(initialValue = "") {
        val rnd = java.util.Random()
        val chars = AsciiArt.NOISE_CHARS
        while (true) {
            val sb = StringBuilder()
            for (i in 0 until 30) sb.append(chars[rnd.nextInt(chars.length)])
            value = sb.toString()
            delay(160)
        }
    }
    val jit by produceState(initialValue = 0f) {
        val rnd = java.util.Random()
        while (true) {
            value = if (rnd.nextInt(14) == 0) (rnd.nextFloat() - 0.5f) * 8f else 0f
            delay(110)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Text(
                    "LAIN://NOTES",
                    style = mono(21, Color(0xFFFF3860).copy(alpha = 0.75f), true),
                    modifier = Modifier.offset(x = (-1.5f - jit).dp),
                )
                Text(
                    "LAIN://NOTES",
                    style = mono(21, Color(0xFF30E0FF).copy(alpha = 0.75f), true),
                    modifier = Modifier.offset(x = (1.5f + jit).dp),
                )
                Text("LAIN://NOTES", style = mono(21, pal.fg, true))
            }
            Spacer(Modifier.weight(1f))
            TermButton("CFG", pal.accent, onSettings)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("PRESENT DAY, PRESENT TIME  $clock", style = mono(10, pal.dim))
            Text("NODES:$count", style = mono(10, pal.accent))
        }
        Text(ticker, style = mono(9, pal.line.copy(alpha = 1f)))
        Spacer(Modifier.height(4.dp))
        Rule(pal.line)
    }
}

@Composable
fun NoteCard(pal: Pal, n: Note, onOpen: () -> Unit) {
    val fmt = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.US) }
    val edge = if (n.pinned) pal.accent else pal.line
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, edge)
            .background(pal.bg.copy(alpha = 0.78f))
            .drawBehind { drawRect(pal.accent, size = Size(3.dp.toPx(), size.height)) }
            .clickable { onOpen() }
            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val title = n.title.ifBlank { "untitled_node" }
            Text(
                (if (n.pinned) "[*] " else "> ") + title,
                style = mono(14, pal.accent, true),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(fmt.format(Date(n.ts)), style = mono(9, pal.dim))
        }
        if (n.body.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                n.body.replace('\n', ' ').take(160),
                style = mono(12, pal.fg.copy(alpha = 0.85f)),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ListScreen(
    pal: Pal,
    notes: List<Note>,
    onOpen: (Note) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = if (query.isBlank()) notes else notes.filter {
        it.title.contains(query, true) || it.body.contains(query, true)
    }
    val quote by produceState(initialValue = AsciiArt.QUOTES[0]) {
        var i = 0
        while (true) {
            delay(6000)
            i = (i + 1) % AsciiArt.QUOTES.size
            value = AsciiArt.QUOTES[i]
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Header(pal, notes.size, onSettings)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, pal.line)
                .background(pal.bg.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("> ", style = mono(14, pal.accent, true))
            TermField(query, { query = it }, "search the wired_", pal, Modifier.weight(1f), true, 14)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (shown.isEmpty()) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("... NO SIGNAL ...", style = mono(16, pal.dim, true))
                    Spacer(Modifier.height(6.dp))
                    Text("the wired holds no memories yet.", style = mono(11, pal.dim))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.id }) { n ->
                        NoteCard(pal, n) { onOpen(n) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(quote, style = mono(10, pal.dim))
        Spacer(Modifier.height(6.dp))
        TermButton("[ + NEW NODE ]", pal.accent, onNew, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
    }
}

// ---------------------------------------------------------------------- editor

@Composable
fun EditorScreen(
    pal: Pal,
    note: Note,
    onChange: (Note) -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember(note.id) { mutableStateOf(note.title) }
    var body by remember(note.id) { mutableStateOf(note.body) }
    var pinned by remember(note.id) { mutableStateOf(note.pinned) }
    var confirm by remember { mutableStateOf(false) }

    val saveNow: () -> Unit = {
        val dirty = title != note.title || body != note.body || pinned != note.pinned
        if (dirty && (title.isNotBlank() || body.isNotBlank())) {
            onChange(note.copy(title = title, body = body, pinned = pinned))
        }
    }

    BackHandler {
        saveNow()
        onBack()
    }
    LaunchedEffect(title, body, pinned) {
        delay(700)
        saveNow()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TermButton("< BACK", pal.accent, {
                saveNow()
                onBack()
            })
            Spacer(Modifier.weight(1f))
            TermButton(if (pinned) "PINNED" else "PIN", if (pinned) pal.accent else pal.dim, { pinned = !pinned })
            TermButton("ERASE", pal.danger, { confirm = true })
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(pal.bg.copy(alpha = 0.5f))
                .padding(vertical = 6.dp)
        ) {
            TermField(title, { title = it }, "> title_", pal, Modifier.fillMaxWidth(), true, 18, pal.accent)
        }
        Rule(pal.line)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(pal.bg.copy(alpha = 0.55f))
                .border(1.dp, pal.line)
                .padding(10.dp)
        ) {
            TermField(body, { body = it }, "write something into the wired_", pal, Modifier.fillMaxSize(), false, 14)
        }
        Spacer(Modifier.height(6.dp))
        Text("${body.length} chars  |  autosave on", style = mono(10, pal.dim))
        Spacer(Modifier.height(10.dp))
    }

    if (confirm) {
        Dialog(onDismissRequest = { confirm = false }) {
            Column(
                Modifier
                    .border(1.dp, pal.danger)
                    .background(pal.bg)
                    .padding(16.dp)
            ) {
                Text("ERASE THIS NODE?", style = mono(15, pal.danger, true))
                Spacer(Modifier.height(8.dp))
                Text(title.ifBlank { "untitled_node" }.take(30), style = mono(12, pal.fg))
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "[ CANCEL ]",
                        style = mono(13, pal.dim),
                        modifier = Modifier.clickable { confirm = false },
                    )
                    Text(
                        "[ ERASE ]",
                        style = mono(13, pal.danger, true),
                        modifier = Modifier.clickable {
                            confirm = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------- settings

private fun f2(v: Float): String = String.format(Locale.US, "%.2f", v)

@Composable
fun SettingsScreen(
    cfg: Cfg,
    pal: Pal,
    live: Live,
    noise: ImageBitmap?,
    tick: () -> Int,
    busy: String,
    onCfg: (Cfg) -> Unit,
    onLoadImage: () -> Unit,
    onLoadVideo: () -> Unit,
    onDefault: () -> Unit,
    onRecast: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    val conf = LocalConfiguration.current
    val aspect = conf.screenWidthDp.toFloat() / maxOf(1, conf.screenHeightDp).toFloat()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TermButton("< BACK", pal.accent, onBack)
            Spacer(Modifier.weight(1f))
            Text("WIRED_CONFIG", style = mono(16, pal.fg, true))
        }

        SectionTitle("SIGNAL", pal)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth(0.55f)
                    .aspectRatio(aspect)
                    .border(1.dp, pal.accent)
            ) {
                Backdrop(
                    cfg = cfg,
                    pal = pal,
                    live = live,
                    noise = noise,
                    tick = tick,
                    active = cfg.anim,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = cfg.dim * 0.6f))
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TermButton("IMAGE / GIF", pal.accent, onLoadImage, Modifier.weight(1f))
            TermButton("VIDEO", pal.accent, onLoadVideo, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TermButton("DEFAULT", pal.dim, onDefault, Modifier.weight(1f))
            TermButton("RECAST", pal.accent, onRecast, Modifier.weight(1f))
        }
        if (busy.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(busy, style = mono(12, pal.danger, true))
        }
        Spacer(Modifier.height(4.dp))
        Text("gif / video: the first 9 seconds are cast at 10 fps", style = mono(10, pal.dim))

        SectionTitle("THEME", pal)
        ChipRow(PALS.map { it.name }, cfg.theme, pal) { onCfg(cfg.withTheme(it)) }

        SectionTitle("SCREEN", pal)
        ChipRow(listOf("TV WALL", "ASCII", "OFF"), cfg.mode, pal) { onCfg(cfg.copy(mode = it)) }
        ToggleRow("MIXED CHANNELS (rain / fire / plasma)", cfg.mix, pal) { onCfg(cfg.copy(mix = it)) }

        SectionTitle("IMAGE -> ASCII", pal)
        SliderRow("DETAIL (columns)", cfg.cols.toFloat(), 40f..220f, pal, { it.toInt().toString() }) {
            onCfg(cfg.copy(cols = it.toInt()))
        }
        SliderRow("CONTRAST", cfg.contrast, 0.5f..2.5f, pal, { f2(it) }) { onCfg(cfg.copy(contrast = it)) }
        SliderRow("BRIGHTNESS", cfg.brightness, -0.4f..0.4f, pal, { f2(it) }) { onCfg(cfg.copy(brightness = it)) }
        SliderRow("GAMMA", cfg.gamma, 0.5f..2.2f, pal, { f2(it) }) { onCfg(cfg.copy(gamma = it)) }
        SliderRow("EDGE", cfg.edge, 0f..1f, pal, { f2(it) }) { onCfg(cfg.copy(edge = it)) }
        SliderRow("DITHER", cfg.dither, 0f..1f, pal, { f2(it) }) { onCfg(cfg.copy(dither = it)) }
        SliderRow("ZOOM", cfg.zoom, 1f..3f, pal, { f2(it) }) { onCfg(cfg.copy(zoom = it)) }
        SliderRow("PAN X", cfg.panX, 0f..1f, pal, { f2(it) }) { onCfg(cfg.copy(panX = it)) }
        SliderRow("PAN Y", cfg.panY, 0f..1f, pal, { f2(it) }) { onCfg(cfg.copy(panY = it)) }
        ToggleRow("INVERT", cfg.invert, pal) { onCfg(cfg.copy(invert = it)) }
        ToggleRow("FIT WHOLE IMAGE", cfg.fit, pal) { onCfg(cfg.copy(fit = it)) }
        Spacer(Modifier.height(6.dp))
        Text("CHARSET", style = mono(12, pal.dim))
        Spacer(Modifier.height(4.dp))
        ChipRow(AsciiArt.RAMP_NAMES, cfg.ramp, pal) { onCfg(cfg.copy(ramp = it)) }

        SectionTitle("DISPLAY", pal)
        SliderRow("BACKGROUND DIM", cfg.dim, 0f..0.9f, pal, { f2(it) }) { onCfg(cfg.copy(dim = it)) }
        ToggleRow("ANIMATION", cfg.anim, pal) { onCfg(cfg.copy(anim = it)) }
        ToggleRow("GLITCH TEARS", cfg.glitch, pal) { onCfg(cfg.copy(glitch = it)) }
        ToggleRow("SCANLINES", cfg.scan, pal) { onCfg(cfg.copy(scan = it)) }
        ToggleRow("CABLES", cfg.cables, pal) { onCfg(cfg.copy(cables = it)) }
        ToggleRow("CODE LAYER", cfg.code, pal) { onCfg(cfg.copy(code = it)) }
        ToggleRow("HATCH LINES", cfg.hatch, pal) { onCfg(cfg.copy(hatch = it)) }

        Spacer(Modifier.height(18.dp))
        Text("LAIN://NOTES v2.0  glyphcast edition", style = mono(10, pal.dim))
        Spacer(Modifier.height(24.dp))
    }
}

// ----------------------------------------------------------------------- crash

@Composable
fun CrashScreen(text: String, pal: Pal, onClear: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Text("!! SIGNAL LOST !!", style = mono(18, pal.danger, true))
        Spacer(Modifier.height(4.dp))
        Text("the app crashed last time. send a screenshot of this text:", style = mono(11, pal.dim))
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            SelectionContainer {
                Text(text, style = mono(10, pal.fg))
            }
        }
        Spacer(Modifier.height(8.dp))
        TermButton("CLEAR AND CONTINUE", pal.accent, onClear, Modifier.fillMaxWidth())
    }
}
