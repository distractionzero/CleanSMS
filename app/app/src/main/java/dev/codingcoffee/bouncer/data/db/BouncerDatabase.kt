package dev.codingcoffee.bouncer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MessageEntity::class, MessageFts::class, MmsPartEntity::class, ConversationEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class BouncerDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao
    abstract fun conversations(): ConversationDao

    companion object {
        fun create(context: Context): BouncerDatabase =
            Room.databaseBuilder(context, BouncerDatabase::class.java, "bouncer.db")
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
