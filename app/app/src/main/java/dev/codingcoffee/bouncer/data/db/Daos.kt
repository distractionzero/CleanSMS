package dev.codingcoffee.bouncer.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParts(parts: List<MmsPartEntity>)

    @Query("DELETE FROM mms_parts WHERE messageId = :messageId")
    suspend fun deleteParts(messageId: Long)

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY date DESC, id DESC")
    fun pagingForThread(threadId: Long): PagingSource<Int, MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE threadId = :threadId AND (date > :date OR (date = :date AND id > :id))")
    suspend fun positionInThread(threadId: Long, date: Long, id: Long): Int

    @Query("SELECT * FROM messages WHERE threadId = :threadId AND read = 0 AND box = 1 ORDER BY date ASC")
    suspend fun unreadInThread(threadId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun byId(id: Long): MessageEntity?

    @Query("SELECT * FROM mms_parts WHERE messageId = :messageId ORDER BY id")
    suspend fun parts(messageId: Long): List<MmsPartEntity>

    @Query("SELECT * FROM messages WHERE id IN (:ids) ORDER BY date ASC, id ASC")
    suspend fun byIds(ids: List<Long>): List<MessageEntity>

    @Query("SELECT id FROM messages WHERE threadId = :threadId")
    suspend fun idsInThread(threadId: Long): List<Long>

    @Query("DELETE FROM mms_parts WHERE messageId IN (:ids)")
    suspend fun deletePartsFor(ids: List<Long>)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE messages SET box = :box WHERE id = :id")
    suspend fun updateBox(id: Long, box: Int)

    @Query("UPDATE messages SET read = 1 WHERE threadId = :threadId AND read = 0")
    suspend fun markThreadRead(threadId: Long)

    @Query("DELETE FROM mms_parts WHERE messageId IN (SELECT id FROM messages WHERE threadId = :threadId)")
    suspend fun deleteThreadParts(threadId: Long)

    @Query("DELETE FROM messages WHERE threadId = :threadId")
    suspend fun deleteThread(threadId: Long)

    @Query(
        """
        SELECT threadId, body, MAX(date) AS date, box, hasAttachment,
               SUM(CASE WHEN read = 0 AND box = 1 THEN 1 ELSE 0 END) AS unread
        FROM messages WHERE threadId IN (:threadIds) GROUP BY threadId
        """
    )
    suspend fun statsFor(threadIds: List<Long>): List<ThreadStats>

    @Query(
        """
        SELECT threadId, body, MAX(date) AS date, box, hasAttachment,
               SUM(CASE WHEN read = 0 AND box = 1 THEN 1 ELSE 0 END) AS unread
        FROM messages GROUP BY threadId
        """
    )
    suspend fun statsForAll(): List<ThreadStats>

    @Query(
        """
        SELECT messages.* FROM messages
        JOIN messages_fts ON messages.id = messages_fts.rowid
        WHERE messages_fts MATCH :query ORDER BY messages.date DESC
        """
    )
    fun search(query: String): PagingSource<Int, MessageEntity>

    @Query(
        """
        SELECT COUNT(*) AS total,
               COALESCE(SUM(CASE WHEN box = 1 THEN 1 ELSE 0 END), 0) AS received,
               COALESCE(SUM(CASE WHEN box = 2 THEN 1 ELSE 0 END), 0) AS sent,
               (SELECT COUNT(*) FROM conversations) AS conversations
        FROM messages
        """
    )
    fun stats(): Flow<MessageStats>

    @Query(
        """
        SELECT date(date / 1000, 'unixepoch', 'localtime') AS day, COUNT(*) AS count
        FROM messages WHERE box = 1 AND date >= :since GROUP BY day ORDER BY day
        """
    )
    fun receivedPerDay(since: Long): Flow<List<DailyCount>>

    @Query("DELETE FROM messages WHERE threadId >= :minThreadId")
    suspend fun deleteThreadsFrom(minThreadId: Long)
}

@Dao
interface ConversationDao {
    @Query(
        """
        SELECT * FROM conversations c WHERE archived = :archived AND (:label IS NULL OR (
            SELECT label FROM messages m WHERE m.threadId = c.threadId AND m.box = 1
            ORDER BY m.date DESC, m.id DESC LIMIT 1
        ) = :label) AND (:unreadOnly = 0 OR unreadCount > 0)
        ORDER BY date DESC
        """
    )
    fun paging(archived: Boolean, label: Label?, unreadOnly: Boolean): PagingSource<Int, ConversationEntity>

    @Query("SELECT * FROM conversations WHERE threadId = :threadId")
    fun observe(threadId: Long): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE threadId = :threadId")
    suspend fun byId(threadId: Long): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE threadId IN (:threadIds)")
    suspend fun byIds(threadIds: List<Long>): List<ConversationEntity>

    @Query("SELECT * FROM conversations")
    suspend fun all(): List<ConversationEntity>

    @Upsert
    suspend fun upsert(conversations: List<ConversationEntity>)

    @Query("UPDATE conversations SET archived = :archived WHERE threadId IN (:threadIds)")
    suspend fun setArchived(threadIds: List<Long>, archived: Boolean)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE threadId = :threadId")
    suspend fun clearUnread(threadId: Long)

    @Query("DELETE FROM conversations WHERE threadId IN (:threadIds)")
    suspend fun delete(threadIds: List<Long>)

    @Query("DELETE FROM conversations WHERE threadId >= :minThreadId")
    suspend fun deleteFrom(minThreadId: Long)
}
