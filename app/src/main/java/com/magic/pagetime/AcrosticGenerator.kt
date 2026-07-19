package com.magic.pagetime

import android.content.Context
import org.json.JSONObject
import kotlin.random.Random

object AcrosticGenerator {

    data class LineEntry(val text: String, val isBlank: Boolean = false)

    private const val MIN_TOTAL_LINES = 25
    private const val INDENT = "\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0" // 10 non-breaking spaces

    private var letterBank: Map<Char, List<String>>? = null
    private val rotationIndex = mutableMapOf<Char, Int>()

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

    // Strips punctuation, expands digits into their spelled-out letters
    // (e.g. "2" -> T, w, o), keeps only alphabetic characters.
    private fun expandToLetters(word: String): List<Char> {
        val letters = mutableListOf<Char>()
        for (ch in word) {
            when {
                ch.isDigit() -> digitToWord(ch).forEach { if (it.isLetter()) letters.add(it) }
                ch.isLetter() -> letters.add(ch)
                else -> { /* strip punctuation/symbols */ }
            }
        }
        return letters
    }

    private fun nextLineForLetter(context: Context, letter: Char, random: Random): String {
        val bank = loadBank(context)
        val upper = letter.uppercaseChar()
        val options = bank[upper] ?: return letter.toString()
        if (options.isEmpty()) return letter.toString()
        val idx = rotationIndex.getOrDefault(upper, 0) % options.size
        rotationIndex[upper] = idx + 1
        return options[idx]
    }

    fun generate(context: Context, apiValue: String): List<LineEntry> {
        rotationIndex.clear()
        val random = Random(System.nanoTime())

        val rawWords = apiValue.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val wordLetterLists = rawWords.map { expandToLetters(it) }.filter { it.isNotEmpty() }

        val lines = mutableListOf<LineEntry>()
        var totalLetterCount = 0

        wordLetterLists.forEachIndexed { wIndex, letters ->
            if (wIndex > 0) {
                lines.add(LineEntry("", isBlank = true))
                lines.add(LineEntry("", isBlank = true))
            }
            letters.forEachIndexed { lIndex, ch ->
                val raw = nextLineForLetter(context, ch, random)
                val text = if (lIndex == 0) INDENT + raw else raw
                lines.add(LineEntry(text))
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
                val raw = nextLineForLetter(context, ch, random)
                val text = if (i == 0) INDENT + raw else raw
                lines.add(LineEntry(text))
            }
        }

        return lines
    }
}
