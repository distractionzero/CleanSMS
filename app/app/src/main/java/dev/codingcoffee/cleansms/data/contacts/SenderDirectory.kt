package dev.codingcoffee.cleansms.data.contacts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SenderEntity(val header: String, val name: String, val address: String?)

data class SenderCode(val operator: String?, val serviceArea: String?, val type: String?)

/** Maps DLT sender headers (e.g. KOTAKB in AD-KOTAKB-S) to the registered entity. */
class SenderDirectory(private val context: Context) {
    private val entities by lazy {
        val json = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        val entries = JSONArray(json)
        buildMap {
            for (i in 0 until entries.length()) {
                val results = entries.getJSONObject(i).optJSONArray("results") ?: continue
                for (j in 0 until results.length()) {
                    val result = results.getJSONObject(j)
                    val header = result.getString("header").uppercase()
                    val details = result.optString("principal_entity_and_address")
                    val name = details.substringBefore(',').trim()
                    if (name.isEmpty()) continue
                    val address = details.substringAfter(',', "").trim().trimEnd('.').ifEmpty { null }
                    put(header, SenderEntity(header, name, address))
                }
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

    private companion object {
        const val ASSET = "sender_headers.json"
        const val CODES_ASSET = "sender_codes.json"
    }
}
