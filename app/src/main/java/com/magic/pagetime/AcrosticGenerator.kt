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

    // Fairness tracking: bankKey -> (line text -> times used). bankKey is the
    // letter ("A", "B", ...) or "AND" for the special and-lines pool. Ensures
    // a line only repeats after every other line in its pool has appeared.
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

    // Strips accents (é->e, ö->o, etc.) via Unicode NFD decomposition, then
    // removes the leftover combining marks. Anything that still isn't a
    // plain ASCII letter or digit is left as-is here — it gets filtered out
    // naturally by the segmentation split below (treated as a delimiter).
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

    // mode: "starterOnly" forces a paragraph-opening line, "midOnly" forces a
    // non-starter (avoids two starters in a row), "any" lets fairness alone decide.
    private fun nextLineForLetter(context: Context, letter: Char, mode: String, random: Random): Pair<String, Boolean> {
        val bank = loadBank(context)
        val upper = letter.uppercaseChar()
        val options = bank[upper] ?: return Pair(letter.toString(), false)
        if (options.isEmpty()) return Pair(letter.toString(), false)

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
        val bank = loadBank(context)
        val aLines = bank['A'] ?: return "and"
        val andLines = aLines.filter { it.trim().lowercase().startsWith("and") }
        if (andLines.isEmpty()) return "and"
        return pickFair("AND", andLines, random)
    }

    // Splits the whole input on ANY run of non-alphanumeric characters
    // (spaces, hyphens, brackets, apostrophes, punctuation, unrecognizable
    // symbols/emoji, etc.) in one pass. This means word boundaries AND
    // symbol boundaries both naturally start a new paragraph, with no
    // special-casing needed for each symbol type.
    private fun segmentInput(apiValue: String): List<String> {
        val normalized = normalizeText(apiValue)
        return normalized.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotBlank() }
    }

    fun generate(context: Context, apiValue: String): List<LineEntry> {
        usage.clear()
        val random = Random(System.nanoTime())

        val segments = segmentInput(apiValue)

        val lines = mutableListOf<LineEntry>()
        var totalLineCount = 0
        var lastWasStarter = false
        var isFirstSegment = true

        for (segment in segments) {
            if (!isFirstSegment) {
                lines.add(LineEntry("", isBlank = true))
                lines.add(LineEntry("", isBlank = true))
            }
            isFirstSegment = false

            val isAndWord = segment.equals("and", ignoreCase = true)
            val isPureDigits = segment.all { it.isDigit() }

            when {
                isAndWord -> {
                    val andLine = nextAndLine(context, random)
                    lines.add(LineEntry(INDENT + andLine))
                    lastWasStarter = true
                    totalLineCount++
                }
                isPureDigits && segment.length >= 2 -> {
                    val specialLine = "$segment horses were taken as a bribe in addition to the gold coins"
                    lines.add(LineEntry(INDENT + specialLine))
                    lastWasStarter = true
                    totalLineCount++
                }
                else -> {
                    // Covers: single-digit numbers (spelled via digitToWord),
                    // plain words, and mixed alnum segments.
                    val letters = expandToLetters(segment)
                    if (letters.isEmpty()) continue
                    letters.forEachIndexed { lIndex, ch ->
                        val mode = when {
                            lIndex == 0 -> "starterOnly"
                            lastWasStarter -> "midOnly"
                            else -> "any"
                        }
                        val (raw, wasStarter) = nextLineForLetter(context, ch, mode, random)
                        val text = if (lIndex == 0) INDENT + raw else raw
                        lines.add(LineEntry(text))
                        lastWasStarter = wasStarter
                        totalLineCount++
                    }
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
