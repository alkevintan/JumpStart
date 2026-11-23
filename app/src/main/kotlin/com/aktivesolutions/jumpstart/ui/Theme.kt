package com.aktivesolutions.jumpstart.ui

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** The mockup's colour roles (c.*), one instance per scheme. */
data class JsColors(
    val primary: Color, val onPrimary: Color, val pc: Color, val opc: Color,
    val sec: Color, val osec: Color, val surface: Color,
    val sch1: Color, val sch2: Color, val sch3: Color, val sch4: Color,
    val on: Color, val onv: Color, val outline: Color, val outlineVar: Color,
    val inverseOn: Color, val tert: Color,
)

private fun c(hex: Long) = Color(0xFF000000 or hex)

private fun scheme(
    primary: Long, onPrimary: Long, pc: Long, opc: Long, sec: Long, osec: Long, surface: Long,
    sch1: Long, sch2: Long, sch3: Long, sch4: Long, on: Long, onv: Long, outline: Long, outlineVar: Long,
    inverseOn: Long, tert: Long,
) = JsColors(
    c(primary), c(onPrimary), c(pc), c(opc), c(sec), c(osec), c(surface), c(sch1), c(sch2), c(sch3), c(sch4),
    c(on), c(onv), c(outline), c(outlineVar), c(inverseOn), c(tert),
)

object Schemes {
    const val DYNAMIC = "Dynamic"
    val names = listOf("Purple", "Blue", "Green", "Orange")

    val all: Map<String, JsColors> = mapOf(
        "Purple" to scheme(0xD0BCFF, 0x381E72, 0x4F378B, 0xEADDFF, 0x4A4458, 0xE8DEF8, 0x141218, 0x1D1B20, 0x211F26, 0x2B2930, 0x36343B, 0xE6E0E9, 0xCAC4D0, 0x938F99, 0x49454F, 0x322F35, 0x633B48),
        "Blue" to scheme(0xAAC7FF, 0x0A305F, 0x284777, 0xD6E3FF, 0x3E4759, 0xDAE2F9, 0x111318, 0x191C20, 0x1D2024, 0x282A2F, 0x33353A, 0xE2E2E9, 0xC4C6D0, 0x8E9099, 0x44474E, 0x2E3036, 0x573E5C),
        "Green" to scheme(0x9ED3A2, 0x003918, 0x1E5130, 0xB9F0BC, 0x3A4B3B, 0xD3E8D3, 0x101510, 0x181D18, 0x1C211C, 0x262B26, 0x313631, 0xE0E4DC, 0xC1C9BF, 0x8B9389, 0x414941, 0x2D322C, 0x244C55),
        "Orange" to scheme(0xFFB68A, 0x522300, 0x713700, 0xFFDBC8, 0x5B4132, 0xFFDBC8, 0x1A120D, 0x221A15, 0x271E19, 0x322823, 0x3D332D, 0xF0DFD7, 0xD7C3B7, 0xA08D83, 0x52443B, 0x382E29, 0x4A4A12),
    )

    fun fromMaterial(m: ColorScheme) = JsColors(
        primary = m.primary, onPrimary = m.onPrimary, pc = m.primaryContainer, opc = m.onPrimaryContainer,
        sec = m.secondaryContainer, osec = m.onSecondaryContainer, surface = m.surface,
        sch1 = m.surfaceContainerLow, sch2 = m.surfaceContainer, sch3 = m.surfaceContainerHigh, sch4 = m.surfaceContainerHighest,
        on = m.onSurface, onv = m.onSurfaceVariant, outline = m.outline, outlineVar = m.outlineVariant,
        inverseOn = m.inverseOnSurface, tert = m.tertiaryContainer,
    )

    val dynamicAvailable get() = Build.VERSION.SDK_INT >= 31
}

val LocalJs = staticCompositionLocalOf { Schemes.all.getValue("Purple") }

object Js {
    val c: JsColors @Composable @ReadOnlyComposable get() = LocalJs.current
}

@Composable
fun JumpStartTheme(scheme: String, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val js = if (scheme == Schemes.DYNAMIC && Schemes.dynamicAvailable) {
        Schemes.fromMaterial(dynamicDarkColorScheme(ctx))
    } else Schemes.all[scheme] ?: Schemes.all.getValue("Purple")
    val m = darkColorScheme(
        primary = js.primary, onPrimary = js.onPrimary, primaryContainer = js.pc, onPrimaryContainer = js.opc,
        secondaryContainer = js.sec, onSecondaryContainer = js.osec, surface = js.surface, background = js.surface,
        onSurface = js.on, onBackground = js.on, onSurfaceVariant = js.onv, outline = js.outline, outlineVariant = js.outlineVar,
        inverseOnSurface = js.inverseOn,
    )
    CompositionLocalProvider(LocalJs provides js) {
        MaterialTheme(colorScheme = m, content = content)
    }
}
