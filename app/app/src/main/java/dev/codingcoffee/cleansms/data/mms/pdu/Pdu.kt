package dev.codingcoffee.cleansms.data.mms.pdu

import java.nio.charset.Charset

class MmsPart(val contentType: String, val name: String, val data: ByteArray, val charset: Int = 0) {
    val isText: Boolean get() = contentType.startsWith("text/plain")
    fun text(): String = String(data, Pdu.charset(charset))
}

class Pdu(
    val messageType: Int,
    val transactionId: String?,
    val contentLocation: String?,
    val messageId: String?,
    val from: String?,
    val to: List<String>,
    val cc: List<String>,
    val subject: String?,
    val dateSeconds: Long?,
    val parts: List<MmsPart>,
) {
    companion object {
        const val SEND_REQ = 0x80
        const val NOTIFICATION_IND = 0x82
        const val RETRIEVE_CONF = 0x84

        const val CHARSET_UTF8 = 106

        fun charset(mib: Int): Charset = when (mib) {
            3 -> Charsets.US_ASCII
            4 -> Charsets.ISO_8859_1
            1000, 1015 -> Charsets.UTF_16
            else -> Charsets.UTF_8
        }

        private val wellKnownTypes = mapOf(
            0x00 to "*/*", 0x01 to "text/*", 0x02 to "text/html", 0x03 to "text/plain",
            0x06 to "text/x-vcalendar", 0x07 to "text/x-vcard", 0x1C to "image/*", 0x1D to "image/gif",
            0x1E to "image/jpeg", 0x1F to "image/tiff", 0x20 to "image/png", 0x21 to "image/vnd.wap.wbmp",
            0x22 to "application/vnd.wap.multipart.*", 0x23 to "application/vnd.wap.multipart.mixed",
            0x26 to "application/vnd.wap.multipart.alternative", 0x27 to "application/xml", 0x28 to "text/xml",
            0x33 to "application/vnd.wap.multipart.related", 0x3E to "application/vnd.wap.mms-message",
        )

        fun wellKnownType(code: Int): String = wellKnownTypes[code] ?: "application/octet-stream"
    }
}
