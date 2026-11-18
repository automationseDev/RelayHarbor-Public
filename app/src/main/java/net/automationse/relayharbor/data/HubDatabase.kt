package net.automationse.relayharbor.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "💬",
    val position: Int = 100
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ChannelEntity::class,
        parentColumns = ["id"],
        childColumns = ["channelId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("channelId"), Index("parentMessageId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: Long,
    val sender: String,
    val text: String = "",
    val kind: String = "text",
    val cardJson: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val fromMe: Boolean = false,
    val isRead: Boolean = true,
    @ColumnInfo(defaultValue = "0") val isPinned: Boolean = false,
    @ColumnInfo(defaultValue = "0") val savedForLater: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isDone: Boolean = false,
    @ColumnInfo(defaultValue = "''") val sourcePackage: String = "",
    @ColumnInfo(defaultValue = "NULL") val parentMessageId: Long? = null,
    @ColumnInfo(defaultValue = "''") val aiProfileId: String = ""
)

@Entity(
    tableName = "message_reactions",
    primaryKeys = ["messageId", "emoji"],
    foreignKeys = [ForeignKey(
        entity = MessageEntity::class,
        parentColumns = ["id"],
        childColumns = ["messageId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class MessageReactionEntity(
    val messageId: Long,
    val emoji: String
)

data class ChannelSummary(
    val channelId: Long,
    val lastText: String?,
    val lastSender: String?,
    val lastKind: String?,
    val lastTimestamp: Long?,
    val unreadCount: Int
)

@Entity(tableName = "inbound_events", primaryKeys = ["source", "sequence"])
data class InboundEventEntity(
    val source: String,
    val sequence: Long
)

@Dao
interface HubDao {
    @Query("""
        SELECT r.* FROM message_reactions r
        INNER JOIN messages m ON m.id = r.messageId
        WHERE m.channelId = :channelId
        ORDER BY r.messageId, r.emoji
    """)
    fun observeReactions(channelId: Long): Flow<List<MessageReactionEntity>>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun messageById(id: Long): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE parentMessageId = :id")
    suspend fun replyCount(id: Long): Int

    @Query("SELECT COUNT(*) FROM message_reactions WHERE messageId = :messageId AND emoji = :emoji")
    suspend fun hasReaction(messageId: Long, emoji: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addReaction(reaction: MessageReactionEntity)

    @Query("DELETE FROM message_reactions WHERE messageId = :messageId AND emoji = :emoji")
    suspend fun removeReaction(messageId: Long, emoji: String)

    @Transaction
    suspend fun toggleLocalReaction(messageId: Long, emoji: String) {
        if (messageById(messageId) == null) return
        if (hasReaction(messageId, emoji) > 0) removeReaction(messageId, emoji)
        else addReaction(MessageReactionEntity(messageId, emoji))
    }

    @Query("UPDATE messages SET text = :body WHERE id = :id AND fromMe = 1 AND kind = 'text'")
    suspend fun editOwnMessage(id: Long, body: String): Int

    @Query("""
        DELETE FROM messages WHERE id = :id AND fromMe = 1 AND kind = 'text'
          AND NOT EXISTS (SELECT 1 FROM messages child WHERE child.parentMessageId = :id)
    """)
    suspend fun deleteOwnMessage(id: Long): Int

    @Query("SELECT * FROM channels ORDER BY position, id")
    fun observeChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM messages WHERE channelId = :channelId AND parentMessageId IS NULL ORDER BY timestamp DESC, id DESC LIMIT 100")
    suspend fun recentMessages(channelId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE channelId = :channelId ORDER BY timestamp, id")
    fun observeMessages(channelId: Long): Flow<List<MessageEntity>>

    /**
     * Local-only cross-channel search. instr() treats % and _ as literal characters,
     * avoiding wildcard surprises and expensive unbounded result lists.
     */
    @Query("""
        SELECT * FROM messages
        WHERE instr(lower(text), lower(:term)) > 0
            OR instr(lower(sender), lower(:term)) > 0
            OR instr(lower(COALESCE(cardJson, '')), lower(:term)) > 0
        ORDER BY timestamp DESC, id DESC LIMIT 100
    """)
    suspend fun searchAllMessages(term: String): List<MessageEntity>

    @Query("""
        SELECT c.id AS channelId,
            (SELECT m.text FROM messages m WHERE m.channelId = c.id ORDER BY m.timestamp DESC, m.id DESC LIMIT 1) AS lastText,
            (SELECT m.sender FROM messages m WHERE m.channelId = c.id ORDER BY m.timestamp DESC, m.id DESC LIMIT 1) AS lastSender,
            (SELECT m.kind FROM messages m WHERE m.channelId = c.id ORDER BY m.timestamp DESC, m.id DESC LIMIT 1) AS lastKind,
            (SELECT m.timestamp FROM messages m WHERE m.channelId = c.id ORDER BY m.timestamp DESC, m.id DESC LIMIT 1) AS lastTimestamp,
            (SELECT COUNT(*) FROM messages m WHERE m.channelId = c.id AND m.isRead = 0) AS unreadCount
        FROM channels c ORDER BY c.position, c.id
    """)
    fun observeChannelSummaries(): Flow<List<ChannelSummary>>

    @Query("SELECT * FROM messages WHERE fromMe = 0 ORDER BY timestamp DESC, id DESC LIMIT 500")
    fun observeActivity(): Flow<List<MessageEntity>>

    @Query("SELECT COALESCE(MAX(sequence), 0) FROM inbound_events WHERE source = :source")
    suspend fun lastInboundSequence(source: String): Long

    @Query("SELECT * FROM channels WHERE name = :name LIMIT 1")
    suspend fun findChannelByName(name: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun findChannelById(id: Long): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInboundEvent(event: InboundEventEntity): Long

    @Transaction
    suspend fun saveInboundMessage(event: InboundEventEntity, message: MessageEntity): Boolean {
        if (insertInboundEvent(event) == -1L) return false
        insertMessage(message)
        return true
    }

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun channelCount(): Int

    @Insert
    suspend fun insertChannel(channel: ChannelEntity): Long

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("DELETE FROM channels WHERE id = :channelId")
    suspend fun deleteChannel(channelId: Long)

    @Query("UPDATE messages SET isRead = 1 WHERE channelId = :channelId")
    suspend fun markRead(channelId: Long)

    @Query("UPDATE messages SET isRead = :read WHERE id = :messageId AND fromMe = 0")
    suspend fun setMessageRead(messageId: Long, read: Boolean)

    /** Batched marking for only the explicit visible IDs; avoids altering other channels. */
    @Query("UPDATE messages SET isRead = :read WHERE id IN (:messageIds) AND fromMe = 0")
    suspend fun setMessagesRead(messageIds: List<Long>, read: Boolean)

    @Query("UPDATE messages SET isPinned = :pinned WHERE id = :messageId AND fromMe = 0")
    suspend fun setMessagePinned(messageId: Long, pinned: Boolean)

    @Query("UPDATE messages SET savedForLater = :saved WHERE id = :messageId AND fromMe = 0")
    suspend fun setMessageSaved(messageId: Long, saved: Boolean)

    @Query("UPDATE messages SET isDone = :done, isRead = CASE WHEN :done = 1 THEN 1 ELSE isRead END, savedForLater = CASE WHEN :done = 1 THEN 0 ELSE savedForLater END WHERE id = :messageId AND fromMe = 0")
    suspend fun setMessageDone(messageId: Long, done: Boolean)
}

@Database(
    entities = [ChannelEntity::class, MessageEntity::class, InboundEventEntity::class, MessageReactionEntity::class],
    version = 6, exportSchema = true
)
abstract class HubDatabase : RoomDatabase() {
    abstract fun dao(): HubDao

    companion object {
        @Volatile private var instance: HubDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS inbound_events (
                        source TEXT NOT NULL,
                        sequence INTEGER NOT NULL,
                        PRIMARY KEY(source, sequence)
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN savedForLater INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN isDone INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN sourcePackage TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN parentMessageId INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_parentMessageId ON messages (parentMessageId)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS message_reactions (
                        messageId INTEGER NOT NULL,
                        emoji TEXT NOT NULL,
                        PRIMARY KEY(messageId, emoji),
                        FOREIGN KEY(messageId) REFERENCES messages(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN aiProfileId TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): HubDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                HubDatabase::class.java,
                "relayharbor.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build().also { instance = it }
        }
    }
}
