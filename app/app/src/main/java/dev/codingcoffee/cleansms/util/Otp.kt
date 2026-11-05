package dev.codingcoffee.cleansms.util

import kotlin.math.abs

object Otp {
    private val candidate = Regex("""(?<![\d.,/:-])\d{4,8}(?![\d.,/:-]\d)""")
    private val keyword = Regex("""\b(otp|code|verification|password|passcode|pin)\b""", RegexOption.IGNORE_CASE)
    private val notOtpPrefix = Regex("""(rs\.?|inr|₹|xx|a/c)\s*$""", RegexOption.IGNORE_CASE)

    /** Picks the 4-8 digit number closest to an OTP keyword, skipping amounts and account numbers. */
    fun extract(body: String): String? {
        val candidates = candidate.findAll(body)
            .filterNot { notOtpPrefix.containsMatchIn(body.substring(maxOf(0, it.range.first - 5), it.range.first)) }
            .toList()
        if (candidates.isEmpty()) return null
        val keywords = keyword.findAll(body).map { it.range.first }.toList()
        if (keywords.isEmpty()) return candidates.first().value
        return candidates.minBy { match -> keywords.minOf { abs(it - match.range.first) } }.value
    }
}
