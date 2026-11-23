package com.aktivesolutions.jumpstart.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutsTest {
    private fun ch(c: Char, base: Char? = c.lowercaseChar(), ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false) =
        Chord(base = base, ch = c.code, ctrl = ctrl, alt = alt, shift = shift)
    private fun nav(n: Nav, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false) = Chord(nav = n, ctrl = ctrl, alt = alt, shift = shift)

    // Home
    @Test fun homeLetter() = assertEquals(HomeAct.Search("a"), Shortcuts.home(ch('a')))
    @Test fun homeShifted() = assertEquals(HomeAct.Search("A"), Shortcuts.home(ch('A', 'a', shift = true)))
    @Test fun homePunct() = assertEquals(HomeAct.Search("?"), Shortcuts.home(ch('?', '/', shift = true)))
    @Test fun homeSlashEmpty() = assertEquals(HomeAct.Search(""), Shortcuts.home(ch('/')))
    @Test fun homeCtrlK() = assertEquals(HomeAct.Search(""), Shortcuts.home(Chord(base = 'k', ch = 11, ctrl = true)))
    @Test fun homeSettings() = assertEquals(HomeAct.Settings, Shortcuts.home(Chord(base = ',', ctrl = true)))
    @Test fun homeNotifs() = assertEquals(HomeAct.Notifications, Shortcuts.home(Chord(base = 'n', ctrl = true)))
    @Test fun homeAltDigit() = assertEquals(HomeAct.Launch(2), Shortcuts.home(Chord(base = '3', alt = true)))
    @Test fun homeAlt0None() = assertNull(Shortcuts.home(Chord(base = '0', alt = true)))
    @Test fun homeCtrlLetterNotTyped() = assertNull(Shortcuts.home(ch('a', ctrl = true)))
    @Test fun homeAltLetterNotTyped() = assertNull(Shortcuts.home(ch('a', alt = true)))
    @Test fun homeSpaceEnterIgnored() {
        assertNull(Shortcuts.home(ch(' ', null)))
        assertNull(Shortcuts.home(Chord(nav = Nav.Enter, ch = 10)))
        assertNull(Shortcuts.home(Chord()))
        assertNull(Shortcuts.home(Chord(ch = Int.MIN_VALUE or 0x60))) // dead-key accent flag
    }

    // Search
    @Test fun arrows() {
        assertEquals(SearchAct.Down, Shortcuts.search(nav(Nav.Down)))
        assertEquals(SearchAct.Up, Shortcuts.search(nav(Nav.Up)))
        assertNull(Shortcuts.search(nav(Nav.Up, shift = true)))
    }
    @Test fun enter() {
        assertEquals(SearchAct.Enter, Shortcuts.search(nav(Nav.Enter)))
        assertEquals(SearchAct.Message, Shortcuts.search(nav(Nav.Enter, ctrl = true)))
    }
    @Test fun esc() = assertEquals(SearchAct.Esc, Shortcuts.search(nav(Nav.Esc)))
    @Test fun copyNeedsShift() {
        assertEquals(SearchAct.Copy, Shortcuts.search(Chord(base = 'c', ctrl = true, shift = true)))
        assertNull(Shortcuts.search(Chord(base = 'c', ctrl = true)))
    }
    @Test fun editingKeysUntouched() {
        for (b in "acvx") assertNull(Shortcuts.search(Chord(base = b, ctrl = true)))
        assertNull(Shortcuts.search(ch('a')))
        assertNull(Shortcuts.search(Chord()))
    }
    @Test fun dict() = assertEquals(SearchAct.Dict, Shortcuts.search(Chord(base = 'd', ctrl = true)))
    @Test fun web() {
        assertEquals(SearchAct.Web("web"), Shortcuts.search(Chord(base = 'w', alt = true)))
        assertEquals(SearchAct.Web("store"), Shortcuts.search(Chord(base = 'p', alt = true)))
        assertEquals(SearchAct.Web("maps"), Shortcuts.search(Chord(base = 'm', alt = true)))
        assertEquals(SearchAct.Web("wiki"), Shortcuts.search(Chord(base = 'i', alt = true)))
        assertNull(Shortcuts.search(Chord(base = 'w')))
    }
    @Test fun launch1to8() {
        assertEquals(SearchAct.Launch(0), Shortcuts.search(Chord(base = '1', alt = true)))
        assertEquals(SearchAct.Launch(7), Shortcuts.search(Chord(base = '8', alt = true)))
        assertNull(Shortcuts.search(Chord(base = '9', alt = true)))
    }

    // Highlight movement
    @Test fun move() {
        assertEquals(0, Shortcuts.move(-1, 3, true))
        assertEquals(2, Shortcuts.move(2, 3, true))
        assertEquals(-1, Shortcuts.move(0, 3, false))
        assertEquals(-1, Shortcuts.move(-1, 3, false))
        assertEquals(-1, Shortcuts.move(-1, 0, true))
    }

    @Test fun baseForKeycodes() {
        assertEquals('a', baseFor(29)); assertEquals('k', baseFor(39)); assertEquals('z', baseFor(54))
        assertEquals('1', baseFor(8)); assertEquals('0', baseFor(7)); assertEquals('9', baseFor(16))
        assertEquals(',', baseFor(55)); assertEquals('/', baseFor(76))
        assertNull(baseFor(66)); assertNull(baseFor(0))
    }
    @Test fun altFromMeta() {
        assertEquals(true, altFrom(0x12)) // left alt (+ALT_ON)
        assertEquals(true, altFrom(0x2)) // generic only
        assertEquals(false, altFrom(0x22)) // right alt / AltGr
        assertEquals(false, altFrom(0))
    }
    @Test fun shiftEnterIsEnter() = assertEquals(SearchAct.Enter, Shortcuts.search(nav(Nav.Enter, shift = true)))
    @Test fun altEnterNull() = assertNull(Shortcuts.search(nav(Nav.Enter, alt = true)))
    @Test fun ctrlUpNull() = assertNull(Shortcuts.search(nav(Nav.Up, ctrl = true)))
    @Test fun homeAltGrCharOpensSearch() = assertEquals(HomeAct.Search("@"), Shortcuts.home(Chord(base = 'q', ch = '@'.code))) // alt flag false after altFrom

    @Test fun echoOwnTypingNoReset() {
        val sent = ArrayDeque(listOf("a", "ab"))
        assertEquals(false, echoReset("a", "ab", sent)); assertEquals(listOf("ab"), sent.toList())
        assertEquals(false, echoReset("ab", "ab", sent)); assertEquals(0, sent.size)
    }
    @Test fun echoExternalResets() = assertEquals(true, echoReset("", "abc", ArrayDeque()))
    @Test fun echoABACaseThenExternalClear() {
        val sent = ArrayDeque(listOf("B", "A")) // typed A->B->A before any echo arrived
        assertEquals(false, echoReset("A", "A", sent))
        assertEquals(0, sent.size) // stale entries dropped
        assertEquals(true, echoReset("", "A", sent)) // later Esc/Clear is seen as external
    }
}
