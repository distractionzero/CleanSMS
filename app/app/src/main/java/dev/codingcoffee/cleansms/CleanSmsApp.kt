package dev.codingcoffee.cleansms

import android.app.Application
import android.content.Context
import dev.codingcoffee.cleansms.data.Messaging
import dev.codingcoffee.cleansms.data.classify.Labeler
import dev.codingcoffee.cleansms.data.contacts.ContactRepository
import dev.codingcoffee.cleansms.data.contacts.SenderDirectory
import dev.codingcoffee.cleansms.data.db.CleanSmsDatabase
import dev.codingcoffee.cleansms.data.mms.MmsService
import dev.codingcoffee.cleansms.data.prefs.SettingsStore
import dev.codingcoffee.cleansms.data.sync.TelephonySync
import dev.codingcoffee.cleansms.data.translate.Translations
import dev.codingcoffee.cleansms.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CleanSmsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.ensureChannel()
        container.contacts.onPermissionChanged()
        container.sync.start()
        container.labeler.start()
    }
}

class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(context)
    val db = CleanSmsDatabase.create(context)
    val contacts = ContactRepository(context)
    val senders = SenderDirectory(context)
    val sync = TelephonySync(context, db, settings, scope)
    val notifier: Notifier = Notifier(context, db, contacts, senders, settings) { labeler.labelOf(it) }
    val mms = MmsService(context, db, sync)
    val messaging = Messaging(context, db, sync, mms, notifier)
    val labeler: Labeler = Labeler(context, db, settings, contacts, messaging, scope)
    val translations = Translations(context)
}

val Context.container: AppContainer get() = (applicationContext as CleanSmsApp).container
