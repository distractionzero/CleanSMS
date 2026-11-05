package dev.codingcoffee.cleansms.data.mms.pdu

/** Minimal WAP-209/230 decoder for m-notification-ind and m-retrieve-conf PDUs. */
class PduReader(private val data: ByteArray) {
    private var pos = 0

    fun parse(): Pdu? = runCatching { parseInternal() }.getOrNull()

    private fun parseInternal(): Pdu {
        var type = 0
        var transactionId: String? = null
        var contentLocation: String? = null
        var messageId: String? = null
        var from: String? = null
        val to = mutableListOf<String>()
        val cc = mutableListOf<String>()
        var subject: String? = null
        var date: Long? = null
        var parts = emptyList<MmsPart>()

        while (pos < data.size) {
            val field = u8()
            if (field < 0x80) {
                pos--
                text()
                skipValue()
                continue
            }
            when (field) {
                0x8C -> type = u8()
                0x98 -> transactionId = text()
                0x83 -> contentLocation = text()
                0x8B -> messageId = text()
                0x89 -> from = fromValue()
                0x97 -> to += cleanAddress(encodedString())
                0x82 -> cc += cleanAddress(encodedString())
                0x96 -> subject = encodedString()
                0x85 -> date = longInteger()
                0x84 -> {
                    contentType()
                    parts = multipart()
                    break
                }
                else -> skipValue()
            }
        }
        return Pdu(type, transactionId, contentLocation, messageId, from, to, cc, subject, date, parts)
    }

    private fun multipart(): List<MmsPart> {
        val count = uintVar()
        val parts = ArrayList<MmsPart>(count)
        repeat(count) {
            val headersLength = uintVar()
            val dataLength = uintVar()
            val headersEnd = pos + headersLength
            val (type, charset) = contentType()
            var name = ""
            while (pos < headersEnd) {
                when (val field = u8()) {
                    0x8E -> name = text()
                    0xC0 -> if (name.isEmpty()) name = text().trim('<', '>') else text()
                    else -> if (field < 0x80) {
                        pos--
                        text()
                        skipValue()
                    } else {
                        skipValue()
                    }
                }
            }
            pos = headersEnd
            parts += MmsPart(type, name, data.copyOfRange(pos, pos + dataLength), charset)
            pos += dataLength
        }
        return parts
    }

    private fun contentType(): Pair<String, Int> {
        val b = peek()
        if (b >= 0x80) {
            pos++
            return Pdu.wellKnownType(b and 0x7F) to 0
        }
        if (b >= 0x20) return text() to 0
        val end = valueLength().let { pos + it }
        val m = peek()
        val media = when {
            m >= 0x80 -> Pdu.wellKnownType(u8() and 0x7F)
            m >= 0x20 -> text()
            else -> Pdu.wellKnownType(integer().toInt())
        }
        var charset = 0
        while (pos < end) {
            val p = u8()
            when {
                p == 0x81 -> charset = integer().toInt()
                p >= 0x80 -> skipValue()
                else -> {
                    pos--
                    text()
                    skipValue()
                }
            }
        }
        pos = end
        return media to charset
    }

    private fun fromValue(): String? {
        val end = valueLength().let { pos + it }
        val token = u8()
        val address = if (token == 0x80) cleanAddress(encodedString()) else null
        pos = end
        return address
    }

    private fun encodedString(): String {
        val b = peek()
        if (b > 0x1F) return text()
        val end = valueLength().let { pos + it }
        val charset = integer().toInt()
        if (peek() == 0x7F) pos++
        var stop = pos
        while (stop < end && data[stop].toInt() != 0) stop++
        val value = String(data, pos, stop - pos, Pdu.charset(charset))
        pos = end
        return value
    }

    private fun text(): String {
        val first = peek()
        if (first == 0x7F || first == 0x22) pos++
        val start = pos
        while (pos < data.size && data[pos].toInt() != 0) pos++
        val value = String(data, start, pos - start, Charsets.UTF_8)
        pos++
        return value
    }

    private fun skipValue() {
        val b = peek()
        when {
            b <= 0x1E -> pos += 1 + b
            b == 0x1F -> {
                pos++
                val length = uintVar()
                pos += length
            }
            b < 0x80 -> text()
            else -> pos++
        }
    }

    private fun valueLength(): Int {
        val b = u8()
        return when {
            b < 0x1F -> b
            b == 0x1F -> uintVar()
            else -> error("Invalid value-length")
        }
    }

    private fun integer(): Long {
        val b = peek()
        return if (b >= 0x80) (u8() and 0x7F).toLong() else longInteger()
    }

    private fun longInteger(): Long {
        val length = u8()
        var value = 0L
        repeat(length) { value = (value shl 8) or u8().toLong() }
        return value
    }

    private fun uintVar(): Int {
        var value = 0
        while (true) {
            val b = u8()
            value = (value shl 7) or (b and 0x7F)
            if (b and 0x80 == 0) return value
        }
    }

    private fun peek(): Int = data[pos].toInt() and 0xFF

    private fun u8(): Int = data[pos++].toInt() and 0xFF

    private fun cleanAddress(raw: String): String = raw.substringBefore("/TYPE=")
}
