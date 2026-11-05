package dev.codingcoffee.bouncer.data.classify

import java.io.InputStream
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.ln
import kotlin.math.sqrt

/** TF-IDF (word + char n-grams) linear classifier exported by `export.py`. */
class SmsClassifier private constructor(
    val labels: List<String>,
    private val word: Block,
    private val char: Block,
    private val coef: FloatArray,
    private val intercept: FloatArray,
) {
    private val features = word.idf.size + char.idf.size

    fun scores(text: String): DoubleArray {
        val t = template(text)
        val scores = DoubleArray(labels.size) { intercept[it].toDouble() }
        accumulate(scores, wordTerms(t), word, 0)
        accumulate(scores, charTerms(t), char, word.idf.size)
        return scores
    }

    private fun accumulate(scores: DoubleArray, terms: List<String>, block: Block, offset: Int) {
        val counts = HashMap<Int, Int>()
        for (term in terms) block.terms[term]?.let { counts.merge(it, 1, Int::plus) }
        val values = counts.mapValues { (i, count) -> (1 + ln(count.toDouble())) * block.idf[i] }
        val norm = sqrt(values.values.sumOf { it * it })
        if (norm == 0.0) return
        for ((i, value) in values) {
            for (k in scores.indices) scores[k] += coef[k * features + offset + i] * value / norm
        }
    }

    private class Block(val terms: Map<String, Int>, val idf: FloatArray)

    companion object {
        private const val WS = "\\t\\n\\x0B\\f\\r\\x1C-\\x1F \\x85\\xA0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000"
        private const val W = "[\\p{L}\\p{Nd}\\p{Nl}\\p{No}_]"
        private const val BOUNDARY = "(?:(?<=$W)(?!$W)|(?<!$W)(?=$W))"
        private val URL = Regex(
            "(https?://[^$WS]+|www\\.[^$WS]+|$BOUNDARY[a-z0-9-]+\\.(?:me|io|in|com|ly|co|link)/[^$WS]+)",
            RegexOption.IGNORE_CASE,
        )

        fun load(input: InputStream): SmsClassifier {
            val buf = ByteBuffer.wrap(input.readBytes())
            check(buf.int == 1) { "Unsupported model version" }
            val labels = List(buf.int) { buf.string() }
            val word = buf.block()
            val char = buf.block()
            val coef = buf.floats(labels.size * (word.idf.size + char.idf.size))
            return SmsClassifier(labels, word, char, coef, buf.floats(labels.size))
        }

        private fun ByteBuffer.string(): String {
            val length = int
            return String(array(), position(), length, Charsets.UTF_8).also { position(position() + length) }
        }

        private fun ByteBuffer.floats(n: Int): FloatArray =
            FloatArray(n).also { asFloatBuffer().get(it); position(position() + n * 4) }

        private fun ByteBuffer.block(): Block {
            val n = int
            val terms = HashMap<String, Int>(n * 2)
            repeat(n) { terms[string()] = it }
            return Block(terms, floats(n))
        }

        fun template(text: String): String {
            val urls = URL.replace(text.lowercase(Locale.ROOT), "<url>")
            val digits = StringBuilder()
            var inDigits = false
            urls.codePoints().forEach { cp ->
                if (Character.isDigit(cp)) {
                    if (!inDigits) digits.append("<n>")
                } else {
                    digits.appendCodePoint(cp)
                }
                inDigits = Character.isDigit(cp)
            }
            return tokens(digits) { !isSpace(it) }.joinToString(" ")
        }

        private fun isSpace(cp: Int) = Character.isWhitespace(cp) || Character.isSpaceChar(cp) || cp == 0x85

        private fun isWord(cp: Int) = cp == '_'.code || when (Character.getType(cp).toByte()) {
            Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
            Character.MODIFIER_LETTER, Character.OTHER_LETTER, Character.DECIMAL_DIGIT_NUMBER,
            Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true
            else -> false
        }

        private fun tokens(text: CharSequence, keep: (Int) -> Boolean): List<String> {
            val result = mutableListOf<String>()
            val current = StringBuilder()
            text.codePoints().forEach { cp ->
                if (keep(cp)) {
                    current.appendCodePoint(cp)
                } else if (current.isNotEmpty()) {
                    result += current.toString()
                    current.clear()
                }
            }
            if (current.isNotEmpty()) result += current.toString()
            return result
        }

        private fun wordTerms(t: String): List<String> {
            val words = tokens(t, ::isWord).filter { it.codePointCount(0, it.length) >= 2 }
            return words + words.zipWithNext { a, b -> "$a $b" }
        }

        private fun charTerms(t: String): List<String> {
            val result = mutableListOf<String>()
            for (word in t.split(' ')) {
                val cps = " $word ".codePoints().toArray()
                for (n in 3..5) {
                    var offset = 0
                    result += String(cps, offset, minOf(n, cps.size - offset))
                    while (offset + n < cps.size) {
                        offset++
                        result += String(cps, offset, minOf(n, cps.size - offset))
                    }
                    if (offset == 0) break
                }
            }
            return result
        }
    }
}
