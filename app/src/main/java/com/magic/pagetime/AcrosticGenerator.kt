package com.magic.pagetime

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer
import kotlin.random.Random

object AcrosticGenerator {

    data class LineEntry(val text: String, val isBlank: Boolean = false)

    private const val MIN_TOTAL_LINES = 50
    private const val INDENT = "\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0"

    private var letterBank: Map<Char, List<String>>? = null
    private var andLinesCache: List<String>? = null

    private val usage = mutableMapOf<String, MutableMap<String, Int>>()

    private fun loadBank(context: Context): Map<Char, List<String>> {
        letterBank?.let { return it }
        return try {
            val json = context.assets.open("letter_lines.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            val map = mutableMapOf<Char, List<String>>()
            for (letter in 'A'..'Z') {
                val key = letter.toString()
                if (obj.has(key)) {
                    val arr = obj.getJSONArray(key)
                    val lines = (0 until arr.length()).map { arr.getString(it) }
                    map[letter] = lines
                }
            }
            letterBank = map
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun andLines(context: Context): List<String> {
        andLinesCache?.let { return it }
        val aLines = loadBank(context)['A'] ?: emptyList()
        val result = aLines.filter { it.trim().lowercase().startsWith("and") }
        andLinesCache = result
        return result
    }

    private fun digitToWord(c: Char): String = when (c) {
        '0' -> "Zero"; '1' -> "One"; '2' -> "Two"; '3' -> "Three"; '4' -> "Four"
        '5' -> "Five"; '6' -> "Six"; '7' -> "Seven"; '8' -> "Eight"; '9' -> "Nine"
        else -> ""
    }

    private fun expandToLetters(segment: String): List<Char> {
        val letters = mutableListOf<Char>()
        for (ch in segment) {
            when {
                ch.isDigit() -> digitToWord(ch).forEach { if (it.isLetter()) letters.add(it) }
                ch.isLetter() -> letters.add(ch)
                else -> { }
            }
        }
        return letters
    }

    private fun isStarter(line: String): Boolean {
        val firstChar = line.trim().firstOrNull() ?: return false
        return firstChar.isUpperCase()
    }

    private fun normalizeText(input: String): String {
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "")
    }

    private fun pickFair(bankKey: String, candidates: List<String>, random: Random): String {
        if (candidates.isEmpty()) return ""
        val counts = usage.getOrPut(bankKey) { mutableMapOf() }
        val minCount = candidates.minOf { counts.getOrDefault(it, 0) }
        val leastUsed = candidates.filter { counts.getOrDefault(it, 0) == minCount }
        val chosen = leastUsed[random.nextInt(leastUsed.size)]
        counts[chosen] = (counts[chosen] ?: 0) + 1
        return chosen
    }

    private fun nextLineForLetter(context: Context, letter: Char, mode: String, random: Random): Pair<String, Boolean> {
        val bank = loadBank(context)
        val upper = letter.uppercaseChar()
        var options = bank[upper] ?: return Pair(letter.toString(), false)
        if (options.isEmpty()) return Pair(letter.toString(), false)

        // The dedicated "and" lines are reserved for the solo-word "and"
        // case only — exclude them from normal letter-A selection so they
        // can never be double-picked from two different pools.
        if (upper == 'A') {
            val reserved = andLines(context)
            options = options.filterNot { reserved.contains(it) }
            if (options.isEmpty()) options = bank[upper]!! // safety net if everything got filtered
        }

        val starters = options.filter { isStarter(it) }
        val mids = options.filter { !isStarter(it) }

        val pool = when {
            mode == "starterOnly" && starters.isNotEmpty() -> starters
            mode == "midOnly" && mids.isNotEmpty() -> mids
            else -> options
        }

        val chosen = pickFair(upper.toString(), pool, random)
        return Pair(chosen, isStarter(chosen))
    }

    private fun nextAndLine(context: Context, random: Random): String {
        val lines = andLines(context)
        if (lines.isEmpty()) return "and"
        return pickFair("AND", lines, random)
    }

    // Two-stage cleanup:
    // 1. Soft characters (apostrophe, colon) are silently removed — they
    //    never break a paragraph, just stitch the surrounding letters together.
    // 2. Whatever remains is split on any run of non-alphanumeric characters
    //    (spaces, hyphens, brackets, other punctuation/symbols) — these DO
    //    start a new paragraph.
    private fun segmentInput(apiValue: String): List<String> {
        val normalized = normalizeText(apiValue)
        val softStripped = normalized.replace(Regex("['\u2019:]"), "")
        return softStripped.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotBlank() }
    }

    fun generate(context: Context, apiValue: String): List<LineEntry> {
        usage.clear()
        andLinesCache = null
        val random = Random(System.nanoTime())

        val segments = segmentInput(apiValue)

        val lines = mutableListOf<LineEntry>()
        var totalLineCount = 0
        var lastWasStarter = false
        var suppressNextBreak = false // true right after "and", so the following word doesn't start a new paragraph

        segments.forEachIndexed { segIndex, segment ->
            val isAndWord = segment.equals("and", ignoreCase = true)
            val isPureDigits = segment.all { it.isDigit() }

            val isNewParagraph = segIndex == 0 || (!isAndWord && !suppressNextBreak)

            if (segIndex > 0 && !isAndWord && !suppressNextBreak) {
                lines.add(LineEntry("", isBlank = true))
                lines.add(LineEntry("", isBlank = true))
            }

            when {
                isAndWord -> {
                    // "and" connects the previous and next paragraphs — no
                    // break before it, no indent (it's a continuation line),
                    // and it suppresses the break before the following word too.
                    val andLine = nextAndLine(context, random)
                    lines.add(LineEntry(andLine))
                    lastWasStarter = true
                    totalLineCount++
                    suppressNextBreak = true
                }
                isPureDigits && segment.length >= 2 -> {
                    val specialLine = "$segment horses were taken as a bribe in addition to the gold coins"
                    val text = if (isNewParagraph) INDENT + specialLine else specialLine
                    lines.add(LineEntry(text))
                    lastWasStarter = true
                    totalLineCount++
                    suppressNextBreak = false
                }
                else -> {
                    val letters = expandToLetters(segment)
                    if (letters.isEmpty()) {
                        suppressNextBreak = false
                        return@forEachIndexed
                    }
                    letters.forEachIndexed { lIndex, ch ->
                        val forceStarter = lIndex == 0 && isNewParagraph
                        val mode = when {
                            forceStarter -> "starterOnly"
                            lastWasStarter -> "midOnly"
                            else -> "any"
                        }
                        val (raw, wasStarter) = nextLineForLetter(context, ch, mode, random)
                        val text = if (lIndex == 0 && isNewParagraph) INDENT + raw else raw
                        lines.add(LineEntry(text))
                        lastWasStarter = wasStarter
                        totalLineCount++
                    }
                    suppressNextBreak = false
                }
            }
        }

        if (totalLineCount < MIN_TOTAL_LINES) {
            val needed = MIN_TOTAL_LINES - totalLineCount
            lines.add(LineEntry("", isBlank = true))
            lines.add(LineEntry("", isBlank = true))
            val alphabet = ('A'..'Z').toList()
            for (i in 0 until needed) {
                val ch = alphabet[random.nextInt(alphabet.size)]
                val mode = when {
                    i == 0 -> "starterOnly"
                    lastWasStarter -> "midOnly"
                    else -> "any"
                }
                val (raw, wasStarter) = nextLineForLetter(context, ch, mode, random)
                val text = if (i == 0) INDENT + raw else raw
                lines.add(LineEntry(text))
                lastWasStarter = wasStarter
            }
        }

        return lines
    }
}
