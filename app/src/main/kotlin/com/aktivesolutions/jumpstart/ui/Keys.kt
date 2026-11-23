package com.aktivesolutions.jumpstart.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed

/** Non-character keys the shortcuts care about. */
enum class Nav { Enter, Esc, Up, Down, Other }

/**
 * A key press reduced to plain data so the matching below is JVM-testable. [base] is the *physical* key (lowercase letter,
 * digit, ',' or '/'): with Ctrl/Alt held, unicodeChar is unreliable (control codes, Alt-composed symbols), so chords match
 * on the key identity. [ch] is the code point the key produced (0 if none), which is what makes shifted characters work.
 */
data class Chord(
    val nav: Nav = Nav.Other, val base: Char? = null, val ch: Int = 0,
    val ctrl: Boolean = false, val alt: Boolean = false, val shift: Boolean = false, val meta: Boolean = false,
)

sealed interface HomeAct {
    data class Search(val initial: String) : HomeAct
    data object Settings : HomeAct
    data object Notifications : HomeAct
    data class Launch(val n: Int) : HomeAct // 0-based
}

sealed interface SearchAct {
    data object Down : SearchAct
    data object Up : SearchAct
    data object Enter : SearchAct
    data object Message : SearchAct
    data object Esc : SearchAct
    data object Copy : SearchAct
    data object Dict : SearchAct
    data class Web(val kind: String) : SearchAct
    data class Launch(val n: Int) : SearchAct // 0-based
}

object Shortcuts {
    private fun Chord.digit(): Int? = base?.takeIf { it in '1'..'9' }?.let { it - '1' }
    private fun Chord.plain() = !ctrl && !alt && !meta
    private fun Chord.altOnly() = alt && !ctrl && !shift && !meta
    private fun Chord.ctrlOnly() = ctrl && !alt && !shift && !meta

    fun home(k: Chord): HomeAct? = when {
        k.ctrlOnly() && k.base == 'k' -> HomeAct.Search("")
        k.ctrlOnly() && k.base == ',' -> HomeAct.Settings
        k.ctrlOnly() && k.base == 'n' -> HomeAct.Notifications
        k.altOnly() && k.digit() != null -> HomeAct.Launch(k.digit()!!)
        k.plain() && k.ch == '/'.code -> HomeAct.Search("") // "/" opens search but is not typed into it
        k.plain() && k.nav == Nav.Other && printable(k.ch) -> HomeAct.Search(String(Character.toChars(k.ch)))
        else -> null
    }

    /** Whitespace and control codes don't start a search; negative = dead-key accent flag. */
    fun printable(ch: Int) = ch > ' '.code && ch != 127 && Character.isValidCodePoint(ch) && !Character.isISOControl(ch)

    // Plain Ctrl+A/C/V/X, Shift+arrows, Backspace, Left/Right etc. return null so the text field keeps them.
    fun search(k: Chord): SearchAct? = when {
        k.nav == Nav.Esc -> SearchAct.Esc
        k.nav == Nav.Enter && k.ctrl && !k.alt && !k.meta -> SearchAct.Message
        k.nav == Nav.Enter && !k.alt && !k.meta -> SearchAct.Enter
        k.nav == Nav.Down && !k.ctrl && !k.alt && !k.shift && !k.meta -> SearchAct.Down
        k.nav == Nav.Up && !k.ctrl && !k.alt && !k.shift && !k.meta -> SearchAct.Up
        k.ctrl && k.shift && !k.alt && !k.meta && k.base == 'c' -> SearchAct.Copy
        k.ctrlOnly() && k.base == 'd' -> SearchAct.Dict
        k.altOnly() && k.base == 'w' -> SearchAct.Web("web")
        k.altOnly() && k.base == 'p' -> SearchAct.Web("store")
        k.altOnly() && k.base == 'm' -> SearchAct.Web("maps")
        k.altOnly() && k.base == 'i' -> SearchAct.Web("wiki")
        k.altOnly() && k.digit() != null && k.digit()!! < 8 -> SearchAct.Launch(k.digit()!!)
        else -> null
    }

    /** Step the highlight; [cur] -1 = none. Down stops at the last item, Up from the first goes back to none. */
    fun move(cur: Int, size: Int, down: Boolean): Int =
        if (size == 0) -1 else if (down) minOf(cur + 1, size - 1) else maxOf(cur - 1, -1)
}

/** Android keycode -> physical key char. (Compose's Key.keyCode is the Android code shifted into the high bits; don't use it.) */
fun baseFor(code: Int): Char? = when (code) {
    in 29..54 -> 'a' + (code - 29) // KEYCODE_A..Z
    in 7..16 -> '0' + (code - 7) // KEYCODE_0..9
    55 -> ','
    76 -> '/'
    else -> null
}

/** Left Alt only: right Alt is AltGr on many layouts and produces symbols, not shortcuts. META_ALT_LEFT_ON=0x10, _RIGHT_ON=0x20, _ON=0x2. */
fun altFrom(meta: Int) = (meta and 0x10) != 0 || ((meta and 0x2) != 0 && (meta and 0x20) == 0)

/**
 * Reconcile the search field with the VM query after it changes. [sent] = texts the field itself pushed to the VM that
 * haven't echoed back yet. Returns true if the field must be reset to [query] (an external change: voice, recents, clear).
 */
fun echoReset(query: String, text: String, sent: ArrayDeque<String>): Boolean {
    if (query == text) { sent.clear(); return false } // in sync (also drops stale entries from A->B->A typing)
    val i = sent.indexOf(query)
    if (i >= 0) { repeat(i + 1) { sent.removeFirst() }; return false }
    sent.clear()
    return true
}

fun KeyEvent.toChord(): Chord {
    val k = key
    val base = baseFor(nativeKeyEvent.keyCode)
    val nav = when (k) {
        Key.Enter, Key.NumPadEnter -> Nav.Enter
        Key.Escape -> Nav.Esc
        Key.DirectionUp -> Nav.Up
        Key.DirectionDown -> Nav.Down
        else -> Nav.Other
    }
    return Chord(nav, base, nativeKeyEvent.unicodeChar, isCtrlPressed, altFrom(nativeKeyEvent.metaState), isShiftPressed, isMetaPressed)
}
