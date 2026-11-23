package com.aktivesolutions.jumpstart.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ConverterTest {
    @Test fun kmToMiles() {
        val a = Converter.answerFor("5 km to mi")!!
        assertEquals("Convert", a.label)
        assertEquals("5 km =", a.expr)
        assertEquals("3.1069 mi", a.value)
    }
    @Test fun temperature() {
        assertEquals("212 °F", Converter.answerFor("100c")!!.value)
        assertEquals("100 °C", Converter.answerFor("212 F")!!.value)
        assertEquals("100 °C", Converter.answerFor("212°f")!!.value)
    }
    @Test fun poundsAlias() {
        assertEquals("0.4536 kg", Converter.answerFor("1 lb")!!.value)
        assertEquals("0.9072 kg", Converter.answerFor("2 lbs")!!.value)
    }
    @Test fun lengths() {
        assertEquals("2.54 cm", Converter.answerFor("1 in")!!.value)
        assertEquals("3.2808 ft", Converter.answerFor("1 m")!!.value)
        assertEquals("1 m", Converter.answerFor("3.28084 ft")!!.value)
        assertEquals("39.3701 in", Converter.answerFor("100 cm")!!.value)
    }
    @Test fun mathAnswer() {
        val a = Converter.answerFor("2 + 3 × 4")!!
        assertEquals("Calculator", a.label)
        assertEquals("2 + 3 × 4 =", a.expr)
        assertEquals("14", a.value)
        assertEquals("1,000", Converter.answerFor("10^3")!!.value)
        assertEquals("-3", Converter.answerFor("1-4")!!.value)
    }
    @Test fun notAnAnswer() {
        assertNull(Converter.answerFor("hello"))
        assertNull(Converter.answerFor("42"))          // no operator
        assertNull(Converter.answerFor("2+"))          // parse error
        assertNull(Converter.answerFor("5 parsec"))
        assertNull(Converter.answerFor("1/0"))          // infinite
    }
    @Test fun nonAsciiDigitsIgnored() {
        assertNull(Converter.answerFor("\uFF15 km"))
        assertNull(Converter.answerFor("\u0665+\u0665"))
        assertNull(Converter.answerFor("-".repeat(50_000) + "1"))
    }
    @Test fun divisionAndModuloByZero() {
        assertNull(Converter.answerFor("0/0"))
        assertNull(Converter.answerFor("5%0"))
        assertEquals("0.25", Converter.answerFor("2^-2")!!.value)
    }
    @Test fun answerPresent() { assertNotNull(Converter.answerFor("7*6")) }
}
