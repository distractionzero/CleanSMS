package dev.codingcoffee.bouncer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MessageEntity::class, MessageFts::class, MmsPartEntity::class, ConversationEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class BouncerDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao
    abstract fun conversations(): ConversationDao

    companion object {
        fun create(context: Context): BouncerDatabase =
            Room.databaseBuilder(context, BouncerDatabase::class.java, "bouncer.db")
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN label TEXT")
            }
        }
    }
}
