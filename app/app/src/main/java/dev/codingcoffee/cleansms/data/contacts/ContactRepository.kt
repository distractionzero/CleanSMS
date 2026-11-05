package dev.codingcoffee.cleansms.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.util.LruCache
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@Immutable
data class Contact(val address: String, val name: String?, val photoUri: Uri?) {
    val displayName: String get() = name ?: address
}

@Immutable
data class PhoneEntry(val contactId: Long, val name: String, val number: String, val label: String, val photoUri: Uri?)

class ContactRepository(private val context: Context) {
    private val cache = LruCache<String, Contact>(4000)
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()
    private var observing = false

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun onPermissionChanged() {
        invalidate()
        if (observing || !hasPermission()) return
        observing = true
        context.contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI, true,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = invalidate()
            },
        )
    }

    private fun invalidate() {
        cache.evictAll()
        _version.value++
    }

    fun peek(address: String): Contact? = cache.get(key(address))

    suspend fun get(address: String): Contact = peek(address) ?: withContext(Dispatchers.IO) {
        lookup(address).also { cache.put(key(address), it) }
    }

    private fun lookup(address: String): Contact {
        if (!hasPermission() || address.isBlank()) return Contact(address, null, null)
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
                null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) Contact(address, c.getString(0), c.getString(1)?.let(Uri::parse)) else null
            }
        }.getOrNull() ?: Contact(address, null, null)
    }

    suspend fun search(query: String): List<PhoneEntry> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val uri = if (query.isBlank()) ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        else Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(query))
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
        )
        val seen = HashSet<String>()
        val result = ArrayList<PhoneEntry>()
        context.contentResolver.query(
            uri, projection, null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val number = c.getString(2) ?: continue
                if (!seen.add(c.getLong(0).toString() + key(number))) continue
                val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                    context.resources, c.getInt(3), c.getString(4),
                ).toString()
                result += PhoneEntry(c.getLong(0), c.getString(1) ?: number, number, label, c.getString(5)?.let(Uri::parse))
            }
        }
        result
    }

    companion object {
        fun key(address: String): String =
            PhoneNumberUtils.normalizeNumber(address).ifEmpty { address.lowercase() }
    }
}
