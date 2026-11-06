package dev.codingcoffee.cleansms.data.contacts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class SenderEntity(val header: String, val name: String, val address: String?, val domain: String?, val icon: String?)

data class SenderCode(val operator: String?, val serviceArea: String?, val type: String?)

/** Maps DLT sender headers (e.g. KOTAKB in AD-KOTAKB-S) to the registered entity. */
class SenderDirectory(private val context: Context) {
    private val entities by lazy {
        val json = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        val entries = JSONObject(json)
        val icons = context.assets.list(ICONS_DIR).orEmpty().toSet()
        buildMap {
            for (key in entries.keys()) {
                val header = key.uppercase()
                val entry = entries.getJSONObject(key)
                val details = entry.optString("principal_entity_and_address")
                val name = details.substringBefore(',').trim()
                if (name.isEmpty()) continue
                val address = details.substringAfter(',', "").trim().trimEnd('.').ifEmpty { null }
                val domain = entry.optString("domain").ifEmpty { null }
                val icon = domain?.let { "$it.png" }?.takeIf { it in icons }?.let { "$ICONS_DIR/$it" }
                put(header, SenderEntity(header, name, address, domain, icon))
            }
        }
    }

    private val codes by lazy {
        JSONObject(context.assets.open(CODES_ASSET).bufferedReader().use { it.readText() })
    }

    suspend fun codeOf(address: String): SenderCode? {
        val parts = address.trim().uppercase().split('-')
        if (parts.size < 2 || parts[0].length != 2) return null
        return withContext(Dispatchers.IO) {
            val prefix = parts[0]
            SenderCode(
                operator = codes.getJSONObject("tsps").optJSONObject(prefix.take(1))?.optString("name"),
                serviceArea = codes.getJSONObject("service_areas").optString(prefix.drop(1)).ifEmpty { null },
                type = parts.getOrNull(2)?.let { codes.getJSONObject("types").optString(it) }?.ifEmpty { null },
            )
        }
    }

    suspend fun entityOf(address: String): SenderEntity? {
        val parts = address.trim().uppercase().split('-')
        val header = if (parts.size >= 2) parts[1] else parts[0]
        if (header.isEmpty() || header.any { !it.isLetterOrDigit() }) return null
        return withContext(Dispatchers.IO) { entities[header] }
    }

    /** Sender IDs shaped like XX-NAME or XX-NAME-X whose NAME is missing from the directory, one per NAME. */
    suspend fun unknown(addresses: List<String>): List<String> = withContext(Dispatchers.IO) {
        addresses.map { it.trim().uppercase() }
            .filter { dltPattern.matches(it) }
            .distinctBy { it.split('-')[1] }
            .filter { it.split('-')[1] !in entities }
            .sorted()
    }

    private companion object {
        val dltPattern = Regex("[A-Z]{2}-[A-Z0-9]{3,}(-[A-Z])?")
        const val ASSET = "sender_headers.json"
        const val CODES_ASSET = "sender_codes.json"
        const val ICONS_DIR = "sender_icons"
    }
}
