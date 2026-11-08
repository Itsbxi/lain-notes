package com.lain.notes

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Writes the stack trace of an uncaught exception to a file so the next launch can show it. */
object Crash {
    private fun file(ctx: Context): File = File(ctx.filesDir, "crash.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                file(app).writeText(Log.getStackTraceString(e))
            } catch (ex: Throwable) {
                // nothing more we can do
            }
            prev?.uncaughtException(t, e)
        }
    }

    fun read(ctx: Context): String? {
        return try {
            val f = file(ctx)
            if (f.exists()) f.readText() else null
        } catch (e: Throwable) {
            null
        }
    }

    fun clear(ctx: Context) {
        try {
            file(ctx).delete()
        } catch (e: Throwable) {
            // ignore
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Crash.install(this)
        setContent { Root() }
    }
}

@Composable
fun Root() {
    val ctx = LocalContext.current
    var crash by remember { mutableStateOf(Crash.read(ctx)) }
    val pal = PALS[0]
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = pal.accent,
            background = pal.bg,
            surface = pal.bg,
            onBackground = pal.fg,
            onSurface = pal.fg,
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            val c = crash
            if (c != null) {
                CrashScreen(c, pal) {
                    Crash.clear(ctx)
                    crash = null
                }
            } else {
                App()
            }
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val cfgStore = remember { CfgStore(app) }
    val notesStore = remember { NotesStore(app) }

    var cfg by remember { mutableStateOf(cfgStore.load()) }
    var notes by remember { mutableStateOf(notesStore.load()) }
    var screen by remember { mutableStateOf(0) } // 0 boot, 1 list, 2 editor, 3 settings
    var editing by remember { mutableStateOf<Note?>(null) }
    var clip by remember { mutableStateOf<Clip?>(null) }
    var srcVer by remember { mutableStateOf(0) }
    var castedVer by remember { mutableStateOf(-1) }
    var converted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf("") }
    var canvasW by remember { mutableStateOf(0) }
    var canvasH by remember { mutableStateOf(0) }

    val live = remember { Live() }
    val noise = remember { Engine.noise(128).asImageBitmap() }
    val pal = PALS[cfg.theme.coerceIn(0, PALS.size - 1)]

    val conf = LocalConfiguration.current
    val density = LocalDensity.current.density
    // the real pixel size of the drawing area (falls back to the dp size until measured)
    val usedW = if (canvasW > 0) canvasW else (conf.screenWidthDp * density).toInt()
    val usedH = if (canvasH > 0) canvasH else (conf.screenHeightDp * density).toInt()
    val screenAspect = usedW.toFloat() / maxOf(1, usedH).toFloat()

    // ---- animation plumbing ------------------------------------------------
    val animNeeded = screen == 0 || (cfg.anim && screen != 2)
    SideEffect {
        live.on = animNeeded
        live.mix = cfg.mix
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) { live.run() }
    }
    val tickState = remember { mutableStateOf(0) }
    val tick: () -> Int = remember { { tickState.value } }
    LaunchedEffect(animNeeded) {
        if (animNeeded) {
            while (true) {
                delay(50)
                tickState.value = tickState.value + 1
            }
        }
    }

    SideEffect {
        val act = ctx as? Activity
        if (act != null) {
            act.window.statusBarColor = pal.bg.toArgb()
            act.window.navigationBarColor = pal.bg.toArgb()
        }
    }

    // ---- source picture / clip ---------------------------------------------
    LaunchedEffect(cfg.custom, srcVer) {
        val useCustom = cfg.custom
        val c: Clip? = withContext(Dispatchers.IO) {
            val custom: Clip? = if (useCustom) Engine.loadClip(app) else null
            custom ?: Engine.loadDefault(app)
        }
        clip = c
    }

    // ---- image -> ascii (debounced while sliders move) -----------------------
    LaunchedEffect(cfg.asciiKey(), clip, usedW, usedH, srcVer) {
        val cl = clip
        if (cl != null) {
            if (converted) delay(140)
            val c = cfg
            val aspect = if (c.mode == 1) screenAspect else 1.0f
            val res: List<List<String>>? = withContext(Dispatchers.Default) {
                try {
                    Engine.toLinesAll(cl, if (c.mode == 1) c else c.copy(fit = false), aspect)
                } catch (e: Throwable) {
                    null
                }
            }
            if (res != null && res.isNotEmpty()) {
                live.fps = cl.fps
                live.picW = if (c.mode == 1) usedW.coerceIn(360, 1500) else 560
                live.picH = if (c.mode == 1) usedH.coerceIn(360, 4000) else 0
                live.pic = res
                live.picVer = live.picVer + 1
                if (castedVer != srcVer) {
                    castedVer = srcVer
                    live.castFrom = SystemClock.uptimeMillis()
                }
                converted = true
            }
        }
    }

    // ---- state helpers ----------------------------------------------------
    val setCfg: (Cfg) -> Unit = { n ->
        cfg = n
        cfgStore.save(n)
    }

    val loadMedia: (Uri) -> Unit = { uri ->
        scope.launch {
            busy = "READING..."
            val res: Clip? = withContext(Dispatchers.IO) {
                try {
                    val type = app.contentResolver.getType(uri) ?: ""
                    val r: Clip? = if (type.startsWith("video/")) {
                        Engine.decodeVideo(app, uri) { i, n -> busy = "CASTING $i/$n" }
                    } else if (type == "image/gif") {
                        Engine.decodeGif(app, uri) ?: Engine.decodeStill(app, uri, 1100)?.let { Clip(listOf(it), 1) }
                    } else {
                        Engine.decodeStill(app, uri, 1100)?.let { Clip(listOf(it), 1) }
                    }
                    if (r != null) Engine.saveClip(app, r)
                    r
                } catch (e: Throwable) {
                    null
                }
            }
            if (res != null) {
                setCfg(cfg.copy(custom = true))
                srcVer += 1
                busy = ""
            } else {
                busy = "CAN'T READ THAT FILE"
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) loadMedia(uri)
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) loadMedia(uri)
    }

    val persist: (Note) -> Unit = { n ->
        notesStore.upsert(n)
        notes = notesStore.load()
    }

    BackHandler(enabled = screen == 3) { screen = 1 }

    // ---- layout ---------------------------------------------------------------
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = pal.accent,
            background = pal.bg,
            surface = pal.bg,
            onBackground = pal.fg,
            onSurface = pal.fg,
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(pal.bg)
                .onSizeChanged { sz ->
                    // ignore the keyboard shrinking the window while a note is open
                    if (screen != 2) {
                        canvasW = sz.width
                        canvasH = sz.height
                    }
                }
        ) {
            if (screen == 0) {
                BootScreen(cfg, pal, live, noise, tick) { screen = 1 }
            } else {
                Backdrop(
                    cfg = cfg,
                    pal = pal,
                    live = live,
                    noise = noise,
                    tick = tick,
                    active = animNeeded,
                    modifier = Modifier.fillMaxSize(),
                )
                UnderLayer(cfg, pal)
                val scrim = when (screen) {
                    2 -> (cfg.dim + 0.3f).coerceAtMost(0.92f)
                    3 -> 0.88f
                    else -> cfg.dim
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrim))
                )
                if (screen == 1) {
                    ListScreen(
                        pal = pal,
                        notes = notes,
                        onOpen = { n ->
                            editing = n
                            screen = 2
                        },
                        onNew = {
                            editing = notesStore.newNote()
                            screen = 2
                        },
                        onSettings = { screen = 3 },
                    )
                } else if (screen == 2) {
                    val n = editing
                    if (n != null) {
                        EditorScreen(
                            pal = pal,
                            note = n,
                            onChange = persist,
                            onBack = { screen = 1 },
                            onDelete = {
                                notesStore.delete(n.id)
                                notes = notesStore.load()
                                screen = 1
                            },
                        )
                    }
                } else if (screen == 3) {
                    SettingsScreen(
                        cfg = cfg,
                        pal = pal,
                        live = live,
                        noise = noise,
                        tick = tick,
                        aspect = screenAspect,
                        busy = busy,
                        onCfg = setCfg,
                        onLoadImage = { pickImage.launch("image/*") },
                        onLoadVideo = { pickVideo.launch("video/*") },
                        onDefault = {
                            setCfg(cfg.copy(custom = false))
                            srcVer += 1
                        },
                        onRecast = { live.castFrom = SystemClock.uptimeMillis() },
                        onBack = { screen = 1 },
                    )
                }
            }
            TopLayer(cfg, pal, tick, animNeeded)
        }
    }
}
