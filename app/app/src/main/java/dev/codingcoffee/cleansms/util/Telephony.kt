package dev.codingcoffee.cleansms.util

import android.content.Context
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import java.util.Locale

fun Context.smsManager(): SmsManager =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getSystemService(SmsManager::class.java)
    else @Suppress("DEPRECATION") SmsManager.getDefault()

@Suppress("DEPRECATION", "MissingPermission")
fun Context.ownNumbers(): List<String> = runCatching {
    val subs = getSystemService(SubscriptionManager::class.java)
    subs.activeSubscriptionInfoList.orEmpty().mapNotNull { info ->
        val number = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) subs.getPhoneNumber(info.subscriptionId)
        else info.number
        number?.takeIf { it.isNotBlank() }
    }
}.getOrDefault(emptyList())

@Suppress("MissingPermission")
fun Context.simLabel(subscriptionId: Int): String? = runCatching {
    getSystemService(SubscriptionManager::class.java).getActiveSubscriptionInfo(subscriptionId)
        ?.let { "SIM ${it.simSlotIndex + 1} - ${it.displayName}" }
}.getOrNull()

fun Context.sameNumber(a: String, b: String): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PhoneNumberUtils.areSamePhoneNumber(a, b, countryIso())
    else @Suppress("DEPRECATION") PhoneNumberUtils.compare(a, b)

private fun Context.countryIso(): String =
    getSystemService(TelephonyManager::class.java)?.let { it.networkCountryIso.ifEmpty { it.simCountryIso } }
        ?.ifEmpty { null }?.uppercase() ?: Locale.getDefault().country

private val PHONE_NUMBER = Regex("""\+?[0-9 ()./-]+""")

/** True for phone numbers and short codes; false for sender IDs like `AX-KOTAKB-P` or RCS business IDs. */
fun isPhoneNumber(address: String): Boolean = PHONE_NUMBER.matches(address)

/** Parses `sms:`, `smsto:`, `mms:` and `mmsto:` URIs into recipient addresses. */
fun recipientsFromUri(uri: android.net.Uri?): List<String> =
    uri?.schemeSpecificPart.orEmpty().substringBefore('?')
        .split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
