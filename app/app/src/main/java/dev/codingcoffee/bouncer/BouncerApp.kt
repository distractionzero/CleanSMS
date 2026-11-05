package dev.codingcoffee.bouncer

import android.app.Application
import android.content.Context
import dev.codingcoffee.bouncer.data.Messaging
import dev.codingcoffee.bouncer.data.classify.Labeler
import dev.codingcoffee.bouncer.data.contacts.ContactRepository
import dev.codingcoffee.bouncer.data.db.BouncerDatabase
import dev.codingcoffee.bouncer.data.mms.MmsService
import dev.codingcoffee.bouncer.data.prefs.SettingsStore
import dev.codingcoffee.bouncer.data.sync.TelephonySync
import dev.codingcoffee.bouncer.data.translate.Translations
import dev.codingcoffee.bouncer.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BouncerApp : Application() {
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
    val db = BouncerDatabase.create(context)
    val contacts = ContactRepository(context)
    val sync = TelephonySync(context, db, settings, scope)
    val notifier: Notifier = Notifier(context, db, contacts, settings) { labeler.labelOf(it) }
    val mms = MmsService(context, db, sync)
    val messaging = Messaging(context, db, sync, mms, notifier)
    val labeler: Labeler = Labeler(context, db, settings, contacts, messaging, scope)
    val translations = Translations(context)
}

val Context.container: AppContainer get() = (applicationContext as BouncerApp).container
