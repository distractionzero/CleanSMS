package dev.codingcoffee.cleansms.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MessageEntity::class, MessageFts::class, MmsPartEntity::class, ConversationEntity::class, LabelChangeEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class CleanSmsDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao
    abstract fun conversations(): ConversationDao
    abstract fun labelChanges(): LabelChangeDao

    companion object {
        fun create(context: Context): CleanSmsDatabase =
            Room.databaseBuilder(context, CleanSmsDatabase::class.java, "cleansms.db")
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN label TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `label_changes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`messageId` INTEGER NOT NULL, `threadId` INTEGER NOT NULL, `address` TEXT NOT NULL, " +
                        "`body` TEXT NOT NULL, `receivedAt` INTEGER NOT NULL, `sentAt` INTEGER, `isMms` INTEGER NOT NULL, " +
                        "`hasAttachment` INTEGER NOT NULL, `isGroup` INTEGER NOT NULL, `senderInContacts` INTEGER NOT NULL, " +
                        "`sim` TEXT, `fromLabel` TEXT, `toLabel` TEXT NOT NULL, `modelLabel` TEXT NOT NULL, " +
                        "`modelScores` TEXT NOT NULL, `modelVersion` TEXT NOT NULL, `changedAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_label_changes_messageId` ON `label_changes` (`messageId`)")
            }
        }
    }
}
