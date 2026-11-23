package com.aktivesolutions.jumpstart.search

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Recursive-descent port of the mockup's calc(): + - * / % ^ ( ) unary minus, × and ÷. */
object Calculator {
    class ParseError : Exception()

    fun calc(s: String): Double = Parser(s.replace(Regex("\\s+"), "").replace('×', '*').replace('÷', '/')).parse()

    private class Parser(private val t: String) {
        private var i = 0
        private var depth = 0
        private fun at(): Char? = t.getOrNull(i)
        private fun digitAt(k: Int) = t.getOrNull(k)?.let { it in '0'..'9' } == true

        fun parse(): Double {
            val v = expr()
            if (i != t.length) throw ParseError()
            return v
        }

        // Unary minus binds tighter than ^ (so "-2^2" = (-2)^2 = 4), as in the mockup.
        private fun num(): Double {
            // Bound recursion so a pasted "((((..." or "-----...1" can't StackOverflow the main thread.
            if (++depth > MAX_DEPTH) throw ParseError()
            try {
                if (at() == '(') {
                    i++
                    val v = expr()
                    if (at() != ')') throw ParseError()
                    i++
                    return v
                }
                if (at() == '-') {
                    i++
                    return -num()
                }
                // Hand-rolled /^\d*\.?\d+/ (ASCII digits only, as in JS). A regex \d on Android (ICU)
                // also matches non-ASCII digits, which toDouble() then rejects with NumberFormatException.
                val start = i
                while (digitAt(i)) i++
                if (at() == '.' && digitAt(i + 1)) {
                    i++
                    while (digitAt(i)) i++
                }
                if (i == start) throw ParseError()
                return t.substring(start, i).toDouble()
            } finally {
                depth--
            }
        }

        private fun pow(): Double {
            var v = num()
            while (at() == '^') {
                i++
                v = Math.pow(v, num())
            }
            return v
        }

        private fun term(): Double {
            var v = pow()
            while (at() == '*' || at() == '/' || at() == '%') {
                val o = t[i++]
                val r = pow()
                v = when (o) {
                    '*' -> v * r
                    '/' -> v / r
                    else -> v % r
                }
            }
            return v
        }

        private fun expr(): Double {
            var v = term()
            while (at() == '+' || at() == '-') {
                val o = t[i++]
                val r = term()
                v = if (o == '+') v + r else v - r
            }
            return v
        }
    }

    private const val MAX_DEPTH = 256

    /** toLocaleString("en-US", { maximumFractionDigits: 4 }) */
    fun fmt(n: Double): String {
        val f = DecimalFormat("#,##0.####", DecimalFormatSymbols(Locale.US))
        f.roundingMode = RoundingMode.HALF_UP
        return f.format(n)
    }
}
