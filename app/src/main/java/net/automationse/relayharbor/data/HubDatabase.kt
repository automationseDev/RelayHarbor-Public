package net.automationse.relayharbor.data

import android.content.Context
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
    indices = [Index("channelId")]
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
    val isRead: Boolean = true
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
    @Query("SELECT * FROM channels ORDER BY position, id")
    fun observeChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM messages WHERE channelId = :channelId ORDER BY timestamp DESC, id DESC LIMIT 14")
    suspend fun recentMessages(channelId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE channelId = :channelId ORDER BY timestamp, id")
    fun observeMessages(channelId: Long): Flow<List<MessageEntity>>

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

    @Query("SELECT * FROM messages WHERE fromMe = 0 ORDER BY timestamp DESC, id DESC LIMIT 60")
    fun observeActivity(): Flow<List<MessageEntity>>

    @Query("SELECT COALESCE(MAX(sequence), 0) FROM inbound_events WHERE source = :source")
    suspend fun lastInboundSequence(source: String): Long

    @Query("SELECT * FROM channels WHERE name = :name LIMIT 1")
    suspend fun findChannelByName(name: String): ChannelEntity?

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
}

@Database(
    entities = [ChannelEntity::class, MessageEntity::class, InboundEventEntity::class],
    version = 2, exportSchema = true
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

        fun get(context: Context): HubDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                HubDatabase::class.java,
                "relayharbor.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
