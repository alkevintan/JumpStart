package com.aktivesolutions.jumpstart.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CalculatorTest {
    private fun c(s: String) = Calculator.calc(s)

    @Test fun basicOps() {
        assertEquals(5.0, c("2+3"), 0.0)
        assertEquals(14.0, c("2+3*4"), 0.0)
        assertEquals(2.5, c("5/2"), 0.0)
        assertEquals(1.0, c("10%3"), 0.0)
    }
    @Test fun unicodeOperators() {
        assertEquals(12.0, c("3×4"), 0.0)
        assertEquals(4.0, c("12÷3"), 0.0)
    }
    @Test fun powerAndParens() {
        assertEquals(8.0, c("2^3"), 0.0)
    }
    @Test fun powerIsLeftAssociative() { assertEquals(64.0, c("2^3^2"), 0.0) }
    @Test fun parentheses() { assertEquals(20.0, c("(2+3)*4"), 0.0) }
    @Test fun unaryMinus() {
        assertEquals(-3.0, c("-3"), 0.0)
        assertEquals(7.0, c("10+-3"), 0.0)
        assertEquals(4.0, c("-2^2"), 0.0) // unary binds tighter, as in the mockup
        assertEquals(3.0, c("--3"), 0.0)
    }
    @Test fun whitespaceAndDecimals() { assertEquals(3.75, c(" 1.5 * 2.5 "), 1e-9) }
    @Test fun errors() {
        assertThrows(Calculator.ParseError::class.java) { c("2+") }
        assertThrows(Calculator.ParseError::class.java) { c("(2+3") }
        assertEquals(23.0, c("2 3"), 0.0) // whitespace is stripped first, as in the mockup
        assertThrows(Calculator.ParseError::class.java) { c("5.") }
    }
    @Test fun numberLiteralsMatchJsRegex() {
        assertEquals(0.5, c(".5"), 0.0)
        assertEquals(1.5, c(".5+1"), 0.0)
        assertThrows(Calculator.ParseError::class.java) { c(".") }
        assertThrows(Calculator.ParseError::class.java) { c("1.2.3") }
        assertThrows(Calculator.ParseError::class.java) { c("1..2") }
    }
    @Test fun nonAsciiDigitsAreParseErrorsNotCrashes() {
        assertThrows(Calculator.ParseError::class.java) { c("\uFF15+1") } // full-width 5
        assertThrows(Calculator.ParseError::class.java) { c("\u0665*2") } // Arabic-Indic 5
    }
    @Test fun deepNestingIsParseErrorNotStackOverflow() {
        assertThrows(Calculator.ParseError::class.java) { c("-".repeat(100_000) + "1") }
        assertThrows(Calculator.ParseError::class.java) { c("(".repeat(100_000) + "1" + ")".repeat(100_000)) }
        assertEquals(1.0, c("(".repeat(50) + "1" + ")".repeat(50)), 0.0)
    }
    @Test fun formatting() {
        assertEquals("1,234.5679", Calculator.fmt(1234.56789))
        assertEquals("3", Calculator.fmt(3.0))
        assertEquals("0.3333", Calculator.fmt(1.0 / 3))
    }
}
