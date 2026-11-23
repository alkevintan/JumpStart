package com.aktivesolutions.jumpstart.search

data class Answer(val label: String, val expr: String, val value: String, val app: String)

/** Port of CONV and answerFor() from the mockup. */
object Converter {
    private class Conv(val to: String, val f: (Double) -> Double)

    private val CONV = mapOf(
        "km" to Conv("mi") { it * 0.621371 }, "mi" to Conv("km") { it / 0.621371 },
        "kg" to Conv("lb") { it * 2.20462 }, "lb" to Conv("kg") { it / 2.20462 },
        "c" to Conv("°F") { it * 9 / 5 + 32 }, "f" to Conv("°C") { (it - 32) * 5 / 9 },
        "cm" to Conv("in") { it / 2.54 }, "in" to Conv("cm") { it * 2.54 },
        "m" to Conv("ft") { it * 3.28084 }, "ft" to Conv("m") { it / 3.28084 },
    )

    // [0-9], not \d: JS \d is ASCII-only, but on Android (ICU regex) \d also matches e.g. full-width or
    // Arabic-Indic digits, which toDouble() rejects -> NumberFormatException on the main thread.
    private val convRe =
        Regex("^(-?[0-9]+(?:\\.[0-9]+)?)\\s*(°?c|°?f|km|mi|kg|lbs?|cm|in|m|ft)\\b(?:\\s*(?:to|in)\\s*\\S*)?$")
    private val hasDigit = Regex("[0-9]")
    private val hasOp = Regex("[+\\-*/^%×÷]")
    private val mathChars = Regex("^[0-9\\s+\\-*/^%().×÷]+$")

    fun answerFor(q: String): Answer? {
        val s = q.trim().lowercase()
        // Both the converter and the calculator need at least one ASCII digit: skip the regexes for plain words.
        if (s.none { it in '0'..'9' }) return null
        val cm = convRe.find(s)
        if (cm != null) {
            val unit = cm.groupValues[2].replace("°", "").replace("lbs", "lb")
            val c = CONV[unit]
            if (c != null) {
                val u = if (unit == "c" || unit == "f") "°" + unit.uppercase() else unit
                val n = cm.groupValues[1].toDouble()
                return Answer("Convert", "${cm.groupValues[1]} $u =", Calculator.fmt(c.f(n)) + " " + c.to, "Calculator")
            }
        }
        if (hasDigit.containsMatchIn(s) && hasOp.containsMatchIn(s) && mathChars.matches(s)) {
            try {
                val v = Calculator.calc(s)
                if (v.isFinite()) return Answer("Calculator", q.trim() + " =", Calculator.fmt(v), "Calculator")
            } catch (_: Calculator.ParseError) {
            }
        }
        return null
    }
}
