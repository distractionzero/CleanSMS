package dev.codingcoffee.bouncer.data.mms.pdu

import java.io.ByteArrayOutputStream

/** Minimal WAP-209 encoder for m-send-req PDUs. */
object PduWriter {

    fun sendReq(recipients: List<String>, parts: List<MmsPart>, transactionId: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x8C); out.write(Pdu.SEND_REQ)
        out.write(0x98); out.text(transactionId)
        out.write(0x8D); out.write(0x92)
        out.write(0x85); out.longInteger(System.currentTimeMillis() / 1000)
        out.write(0x89); out.write(0x01); out.write(0x81)
        recipients.forEach { out.write(0x97); out.text(address(it)) }

        out.write(0x84)
        out.withLength {
            write(0xB3)
            write(0x8A); text("<smil>")
            write(0x89); text("application/smil")
        }

        val all = listOf(MmsPart("application/smil", "smil", smil(parts).toByteArray(), Pdu.CHARSET_UTF8)) + parts
        out.uintVar(all.size)
        all.forEach { part ->
            val headers = ByteArrayOutputStream().apply {
                withLength {
                    text(part.contentType)
                    if (part.charset != 0) {
                        write(0x81); write(0x80 or part.charset)
                    }
                    write(0x85); text(part.name)
                }
                write(0xC0); write(0x22); text("<${part.name}>")
                write(0x8E); text(part.name)
            }
            out.uintVar(headers.size())
            out.uintVar(part.data.size)
            headers.writeTo(out)
            out.write(part.data)
        }
        return out.toByteArray()
    }

    private fun smil(parts: List<MmsPart>): String {
        val body = parts.joinToString("") { part ->
            when {
                part.contentType.startsWith("image/") -> """<img src="${part.name}" region="Image"/>"""
                part.isText -> """<text src="${part.name}" region="Text"/>"""
                else -> ""
            }
        }
        return """<smil><head><layout><root-layout/>""" +
            """<region id="Image" fit="meet" top="0" left="0" height="80%" width="100%"/>""" +
            """<region id="Text" top="80%" left="0" height="20%" width="100%"/>""" +
            """</layout></head><body><par dur="5000ms">$body</par></body></smil>"""
    }

    private fun address(raw: String): String =
        if (raw.contains('@')) raw else raw.filter { it.isDigit() || it == '+' } + "/TYPE=PLMN"

    private fun ByteArrayOutputStream.text(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.isNotEmpty() && (bytes[0].toInt() and 0xFF) >= 0x80) write(0x7F)
        write(bytes)
        write(0)
    }

    private fun ByteArrayOutputStream.longInteger(value: Long) {
        val bytes = ArrayList<Int>()
        var v = value
        do {
            bytes.add(0, (v and 0xFF).toInt())
            v = v shr 8
        } while (v > 0)
        write(bytes.size)
        bytes.forEach { write(it) }
    }

    private fun ByteArrayOutputStream.uintVar(value: Int) {
        val groups = ArrayList<Int>()
        var v = value
        do {
            groups.add(0, v and 0x7F)
            v = v ushr 7
        } while (v > 0)
        groups.forEachIndexed { i, g -> write(if (i < groups.lastIndex) g or 0x80 else g) }
    }

    private inline fun ByteArrayOutputStream.withLength(block: ByteArrayOutputStream.() -> Unit) {
        val inner = ByteArrayOutputStream().apply(block)
        val length = inner.size()
        if (length < 0x1F) write(length) else {
            write(0x1F)
            uintVar(length)
        }
        inner.writeTo(this)
    }
}
