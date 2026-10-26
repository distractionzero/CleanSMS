package dev.codingcoffee.bouncer.util

import android.content.Context
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.telephony.SubscriptionManager

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

fun sameNumber(a: String, b: String): Boolean = PhoneNumberUtils.compare(a, b)

/** Parses `sms:`, `smsto:`, `mms:` and `mmsto:` URIs into recipient addresses. */
fun recipientsFromUri(uri: android.net.Uri?): List<String> =
    uri?.schemeSpecificPart.orEmpty().substringBefore('?')
        .split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
