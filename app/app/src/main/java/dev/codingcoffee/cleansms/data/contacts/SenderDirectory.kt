package dev.codingcoffee.cleansms.data.contacts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Maps DLT sender headers (e.g. KOTAKB in AD-KOTAKB-S) to the registered entity name. */
class SenderDirectory(private val context: Context) {
    private val entities by lazy {
        val json = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        val entries = JSONArray(json)
        buildMap {
            for (i in 0 until entries.length()) {
                val results = entries.getJSONObject(i).optJSONArray("results") ?: continue
                for (j in 0 until results.length()) {
                    val result = results.getJSONObject(j)
                    val name = result.optString("principal_entity_and_address").substringBefore(',').trim()
                    if (name.isNotEmpty()) put(result.getString("header").uppercase(), name)
                }
            }
        }
    }

    suspend fun entityOf(address: String): String? {
        val parts = address.trim().uppercase().split('-')
        val header = if (parts.size >= 2) parts[1] else parts[0]
        if (header.isEmpty() || header.any { !it.isLetterOrDigit() }) return null
        return withContext(Dispatchers.IO) { entities[header] }
    }

    private companion object {
        const val ASSET = "sender_headers.json"
    }
}
