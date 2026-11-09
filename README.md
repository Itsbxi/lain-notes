# LAIN://NOTES v2.3 (glyphcast edition)

Notes app with a Serial Experiments Lain look: a wall of CRT TVs that show your
picture, GIF or video as live ASCII art.

## Build (GitHub Actions)
Push this folder's contents to a GitHub repo (the folder itself is the repo root).
`.github/workflows/build-apk.yml` builds a debug APK on every push, or run it by hand
from **Actions > Build APK > Run workflow**. Download `lain-notes-debug-apk` from the
run's Artifacts; unzip it to get `app-debug.apk`.

The APK is signed with `app/debug.keystore`, so every build has the same signature and
installs over the previous one (your notes stay).

## Build (Android Studio)
Open the folder, let Gradle sync, then Build > Build APK(s).

## In the app
- CFG > IMAGE / GIF or VIDEO: pick any picture, GIF or clip; it is converted to ASCII.
- Sliders: detail, contrast, brightness, gamma, edge, dither, zoom, pan, charset.
- Themes: TV-BLUE, NAVI-TEAL, ID-RED, ZINE-BLUE, WIRED.
- RECAST replays the glyph-cast reveal.
- If the app ever crashes, the next launch shows the error text on screen.

## Fit to screen
- CFG > FIT TO SCREEN > AUTO FIT picks the letter count for your screen, frames the picture and tunes the tones.
- WIDTH / HEIGHT / SHIFT / LINE SPACING sliders adjust the ASCII screen mode by hand.
- RESET TO DEFAULT SETTINGS restores the tuned defaults.
- The app bundles DejaVu Sans Mono (see assets/licenses) because some phones replace the system monospace font.
