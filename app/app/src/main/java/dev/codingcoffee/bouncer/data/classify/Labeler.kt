package dev.codingcoffee.bouncer.data.classify

import android.content.Context
import androidx.room.withTransaction
import dev.codingcoffee.bouncer.BuildConfig
import dev.codingcoffee.bouncer.data.db.BouncerDatabase
import dev.codingcoffee.bouncer.data.db.Label
import dev.codingcoffee.bouncer.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

sealed interface LabelState {
    data object Idle : LabelState
    data class Sorting(val done: Int, val total: Int) : LabelState
}

/** Labels every received message that has no label yet, including ones that arrive later. */
class Labeler(
    private val context: Context,
    private val db: BouncerDatabase,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val classifier by lazy { context.assets.open(BuildConfig.SMS_MODEL_ASSET).use(SmsClassifier::load) }
    private val labels by lazy { classifier.labels.map(Label::valueOf) }

    private val _state = MutableStateFlow<LabelState>(LabelState.Idle)
    val state: StateFlow<LabelState> = _state.asStateFlow()

    fun start() {
        scope.launch {
            if (settings.labelModel != BuildConfig.SMS_MODEL_ASSET) {
                db.messages().clearLabels()
                settings.labelModel = BuildConfig.SMS_MODEL_ASSET
            }
            db.messages().observeUnlabelledCount().conflate().collect { if (it > 0) labelPending(it) }
        }
    }

    private suspend fun labelPending(pending: Int) {
        val dao = db.messages()
        val showProgress = pending >= SHOW_PROGRESS_FROM
        var done = 0
        var total = pending
        while (true) {
            val batch = dao.unlabelled(BATCH)
            if (batch.isEmpty()) break
            if (showProgress) _state.value = LabelState.Sorting(done, total)
            val predicted = batch.map { it.id to labels[classifier.predict(it.body)] }
            db.withTransaction { predicted.forEach { (id, label) -> dao.setLabel(id, label) } }
            done += batch.size
            total = done + dao.unlabelledCount()
        }
        _state.value = LabelState.Idle
    }

    private companion object {
        const val BATCH = 500
        const val SHOW_PROGRESS_FROM = 50
    }
}
