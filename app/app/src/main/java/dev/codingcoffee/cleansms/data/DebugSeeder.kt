package dev.codingcoffee.cleansms.data

import androidx.room.withTransaction
import dev.codingcoffee.cleansms.data.db.CleanSmsDatabase
import dev.codingcoffee.cleansms.data.db.Box
import dev.codingcoffee.cleansms.data.db.ConversationEntity
import dev.codingcoffee.cleansms.data.db.MessageEntity
import dev.codingcoffee.cleansms.data.sync.TelephonySync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Fills the local database (not the system provider) with fake messages for performance testing. */
object DebugSeeder {
    private const val BASE_THREAD = 9_000_000_000L
    private const val BASE_ID = 9_000_000_000L
    private val words = (
        "hey ok sure running late see you soon dinner tonight call me back meeting tomorrow thanks " +
            "awesome sounds good where are you on my way lunch coffee weekend plans happy birthday movie " +
            "train delayed parking outside home office flight landed love it great idea maybe later"
        ).split(' ')

    suspend fun seed(db: CleanSmsDatabase, sync: TelephonySync, total: Int = 50_000, threads: Int = 250) =
        withContext(Dispatchers.IO) {
            val random = Random(42)
            val threadIds = (0 until threads).map { BASE_THREAD + it }
            db.conversations().upsert(threadIds.mapIndexed { i, id ->
                ConversationEntity(id, "+1555%07d".format(i), "", 0, 0, false, false)
            })
            val now = System.currentTimeMillis()
            var date = now
            (0 until total).chunked(1000).forEach { chunk ->
                val batch = chunk.map { i ->
                    date -= random.nextLong(5_000, 900_000)
                    val r = random.nextDouble()
                    val thread = (r * r * threads).toInt().coerceIn(0, threads - 1)
                    val incoming = random.nextBoolean()
                    MessageEntity(
                        id = BASE_ID + i,
                        threadId = threadIds[thread],
                        address = "+1555%07d".format(thread),
                        body = List(random.nextInt(2, 24)) { words.random(random) }.joinToString(" ")
                            .replaceFirstChar { it.uppercase() },
                        date = date,
                        box = if (incoming) Box.INBOX else Box.SENT,
                        read = !incoming || i > 40,
                        isMms = false,
                        hasAttachment = false,
                    )
                }
                db.messages().insertMessages(batch)
            }
            sync.refreshThreads(threadIds)
        }

    suspend fun clear(db: CleanSmsDatabase) = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.messages().deleteThreadsFrom(BASE_THREAD)
            db.conversations().deleteFrom(BASE_THREAD)
        }
    }
}
