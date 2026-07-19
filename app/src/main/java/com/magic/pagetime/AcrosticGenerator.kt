package com.magic.pagetime

import android.content.Context
import org.json.JSONObject
import kotlin.random.Random

object AcrosticGenerator {

    data class LineEntry(val text: String, val isBlank: Boolean = false)

    private const val MIN_TOTAL_LINES = 25
    private const val INDENT = "\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0"

    private var letterBank: Map<Char, List<String>>? = null
    private val rotationIndex = mutableMapOf<String, Int>()

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

    private fun expandToLetters(word: String): List<Char> {
        val letters = mutableListOf<Char>()
        for (ch in word) {
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

    private fun splitByType(options: List<String>): Pair<List<String>, List<String>> {
        val starters = options.filter { isStarter(it) }
        val mids = options.filter { !isStarter(it) }
        return Pair(starters, mids)
    }

    private fun nextLineForLetter(context: Context, letter: Char, mode: String): Pair<String, Boolean> {
        val bank = loadBank(context)
        val upper = letter.uppercaseChar()
        val options = bank[upper] ?: return Pair(letter.toString(), false)
        if (options.isEmpty()) return Pair(letter.toString(), false)

        val (starters, mids) = splitByType(options)

        val pool = when {
            mode == "starterOnly" && starters.isNotEmpty() -> starters
            mode == "midOnly" && mids.isNotEmpty() -> mids
            else -> options
        }
        val poolTag = when {
            mode == "starterOnly" && starters.isNotEmpty() -> "starter"
            mode == "midOnly" && mids.isNotEmpty() -> "mid"
            else -> "any"
        }

        val rotationKey = "${upper}_$poolTag"
        val idx = rotationIndex.getOrDefault(rotationKey, 0) % pool.size
        rotationIndex[rotationKey] = idx + 1

        val chosen = pool[idx]
        return Pair(chosen, isStarter(chosen))
    }

    fun generate(context: Context, apiValue: String): List<LineEntry> {
        rotationIndex.clear()
        val random = Random(System.nanoTime())

        val rawWords = apiValue.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

        val lines = mutableListOf<LineEntry>()
        var totalLetterCount = 0
        var lastWasStarter = false
        var isFirstWord = true

        for (word in rawWords) {
            if (!isFirstWord) {
                lines.add(LineEntry("", isBlank = true))
                lines.add(LineEntry("", isBlank = true))
            }
            isFirstWord = false

            // Standalone numbers longer than 2 digits become their own
            // single-line paragraph using the fixed template, instead of
            // being spelled out letter by letter.
            if (word.all { it.isDigit() } && word.length > 2) {
                val specialLine = "$word horses were taken as a bribe in addition to the gold coins"
                lines.add(LineEntry(INDENT + specialLine))
                lastWasStarter = true // acts as a paragraph opener
                totalLetterCount++
                continue
            }

            val letters = expandToLetters(word)
            if (letters.isEmpty()) continue

            letters.forEachIndexed { lIndex, ch ->
                val mode = when {
                    lIndex == 0 -> "starterOnly"
                    lastWasStarter -> "midOnly"
                    else -> "any"
                }
                val (raw, wasStarter) = nextLineForLetter(context, ch, mode)
                val text = if (lIndex == 0) INDENT + raw else raw
                lines.add(LineEntry(text))
                lastWasStarter = wasStarter
                totalLetterCount++
            }
        }

        if (totalLetterCount < MIN_TOTAL_LINES) {
            val needed = MIN_TOTAL_LINES - totalLetterCount
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
                val (raw, wasStarter) = nextLineForLetter(context, ch, mode)
                val text = if (i == 0) INDENT + raw else raw
                lines.add(LineEntry(text))
                lastWasStarter = wasStarter
            }
        }

        return lines
    }
}
