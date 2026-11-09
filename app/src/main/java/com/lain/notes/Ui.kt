package com.lain.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** DejaVu Sans Mono is bundled: some phones swap the system monospace font for a proportional one. */
val MonoFamily: FontFamily = FontFamily(
    Font(R.font.dejavu_sans_mono, FontWeight.Normal),
    Font(R.font.dejavu_sans_mono_bold, FontWeight.Bold),
)

fun mono(size: Int, color: Color, bold: Boolean = false): TextStyle =
    TextStyle(
        fontFamily = MonoFamily,
        fontSize = size.sp,
        color = color,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )

@Composable
fun TermButton(label: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .border(1.dp, color)
            .background(color.copy(alpha = 0.10f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = mono(13, color, true))
    }
}

@Composable
fun Rule(color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

@Composable
fun SectionTitle(text: String, pal: Pal) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp)) {
        Text("// $text", style = mono(12, pal.accent, true))
        Spacer(Modifier.height(4.dp))
        Rule(pal.line)
    }
}

@Composable
fun ChipRow(options: List<String>, selected: Int, pal: Pal, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (i in options.indices) {
            val on = i == selected
            val c = if (on) pal.accent else pal.dim
            Box(
                Modifier
                    .border(1.dp, c)
                    .background(if (on) c.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(options[i], style = mono(12, c, on))
            }
        }
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    pal: Pal,
    fmt: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = mono(12, pal.dim))
            Text(fmt(value), style = mono(12, pal.fg))
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = pal.accent,
                activeTrackColor = pal.accent,
                inactiveTrackColor = pal.line,
            ),
        )
    }
}

@Composable
fun ToggleRow(label: String, on: Boolean, pal: Pal, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!on) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = mono(13, pal.fg))
        Text(
            if (on) "[ ON  ]" else "[ OFF ]",
            style = mono(13, if (on) pal.accent else pal.dim, on),
        )
    }
}

@Composable
fun TermField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    pal: Pal,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    size: Int = 14,
    color: Color = pal.fg,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = singleLine,
        textStyle = mono(size, color),
        cursorBrush = SolidColor(pal.accent),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, style = mono(size, pal.dim))
                }
                inner()
            }
        },
    )
}

@Composable
fun HSpace(w: Int) {
    Spacer(Modifier.width(w.dp))
}
