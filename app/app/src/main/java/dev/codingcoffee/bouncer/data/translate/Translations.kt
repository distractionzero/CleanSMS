package dev.codingcoffee.bouncer.data.translate

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import android.util.LruCache
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextLanguage
import android.view.translation.TranslationCapability
import android.view.translation.TranslationContext
import android.view.translation.TranslationManager
import android.view.translation.TranslationRequest
import android.view.translation.TranslationRequestValue
import android.view.translation.TranslationResponse
import android.view.translation.TranslationSpec
import android.view.translation.Translator
import androidx.annotation.RequiresApi
import dev.codingcoffee.bouncer.data.db.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

sealed interface OnDeviceTranslation {
    data class Translated(val text: String) : OnDeviceTranslation
    /** The system can translate this language once its pack is downloaded. */
    data object NeedsDownload : OnDeviceTranslation
    data object Unavailable : OnDeviceTranslation
}

class Translations(private val context: Context) {
    private val languages = LruCache<Long, String>(1000)

    /** Language code of [message] when it is confidently not English, else null. */
    suspend fun foreignLanguage(message: MessageEntity): String? = withContext(Dispatchers.Default) {
        languages[message.id]?.let { return@withContext it.ifEmpty { null } }
        val classifier = context.getSystemService(TextClassificationManager::class.java).textClassifier
        val result = classifier.detectLanguage(TextLanguage.Request.Builder(message.body).build())
        val language = result.takeIf { it.localeHypothesisCount > 0 }?.getLocale(0)
            ?.takeIf { it.language != ENGLISH && result.getConfidenceScore(it) >= MIN_CONFIDENCE }
            ?.language
        languages.put(message.id, language.orEmpty())
        language
    }

    /** English translation using the system's on-device translator. */
    suspend fun translateOnDevice(text: String, from: String): OnDeviceTranslation =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { translateWithSystem(text, from) }
                .onFailure { Log.w(TAG, "on-device translation failed", it) }
                .getOrDefault(OnDeviceTranslation.Unavailable)
        } else OnDeviceTranslation.Unavailable

    /** Opens the system screen for downloading translation languages; false when there is none. */
    fun openLanguageDownloads(activity: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val intent = context.getSystemService(TranslationManager::class.java)
            ?.onDeviceTranslationSettingsActivityIntent ?: return false
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
        return runCatching { intent.send(activity, 0, null, null, null, null, options.toBundle()) }.isSuccess
    }

    /** Hands [text] to the user's translate app; false when none is installed. */
    fun openTranslateApp(activity: Context, text: String): Boolean {
        val intents = listOf(
            Intent(Intent.ACTION_TRANSLATE).putExtra(Intent.EXTRA_TEXT, text),
            Intent(Intent.ACTION_PROCESS_TEXT)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true),
        )
        return intents.any { intent ->
            try {
                activity.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun translateWithSystem(text: String, from: String): OnDeviceTranslation {
        val manager = context.getSystemService(TranslationManager::class.java) ?: return OnDeviceTranslation.Unavailable
        val capability = withContext(Dispatchers.IO) {
            manager.getOnDeviceTranslationCapabilities(TranslationSpec.DATA_FORMAT_TEXT, TranslationSpec.DATA_FORMAT_TEXT)
        }.firstOrNull {
            it.state != TranslationCapability.STATE_NOT_AVAILABLE &&
                it.sourceSpec.locale.language == from && it.targetSpec.locale.language == ENGLISH
        } ?: return OnDeviceTranslation.Unavailable
        if (capability.state != TranslationCapability.STATE_ON_DEVICE) return OnDeviceTranslation.NeedsDownload

        val translator = suspendCancellableCoroutine<Translator?> { cont ->
            manager.createOnDeviceTranslator(
                TranslationContext.Builder(capability.sourceSpec, capability.targetSpec).build(),
                Runnable::run,
            ) { cont.resume(it) }
        } ?: return OnDeviceTranslation.Unavailable
        try {
            val request = TranslationRequest.Builder()
                .setTranslationRequestValues(listOf(TranslationRequestValue.forText(text)))
                .build()
            val response = suspendCancellableCoroutine<TranslationResponse> { cont ->
                translator.translate(request, CancellationSignal(), Runnable::run) { cont.resume(it) }
            }
            if (response.translationStatus != TranslationResponse.TRANSLATION_STATUS_SUCCESS) return OnDeviceTranslation.Unavailable
            val translated = response.translationResponseValues.takeIf { it.size() > 0 }?.valueAt(0)?.text?.toString()
            return translated?.let { OnDeviceTranslation.Translated(it) } ?: OnDeviceTranslation.Unavailable
        } finally {
            translator.destroy()
        }
    }

    private companion object {
        const val TAG = "Translations"
        const val ENGLISH = "en"
        const val MIN_CONFIDENCE = 0.7f
    }
}
