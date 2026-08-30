package com.example.storage

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        if (value == null) return "[]"
        val array = JSONArray()
        value.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(value)
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        } catch (e: Exception) {
            return value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        return list
    }
}

@Dao
interface AgentDao {
    @Query("SELECT * FROM agents ORDER BY createdAt ASC")
    fun getAllAgentsFlow(): Flow<List<AgentEntity>>

    @Query("SELECT * FROM agents ORDER BY createdAt ASC")
    suspend fun getAllAgents(): List<AgentEntity>

    @Query("SELECT * FROM agents WHERE id = :id LIMIT 1")
    suspend fun getAgentById(id: String): AgentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAgent(agent: AgentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgents(agents: List<AgentEntity>)

    @Query("DELETE FROM agents WHERE id = :id")
    suspend fun deleteAgentById(id: String)

    @Query("DELETE FROM agents")
    suspend fun clearAllAgents()
}

@Database(
    entities = [
        AgentEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        AgentTaskExecutionEntity::class,
        VoiceTaskEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun agentDao(): AgentDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun agentTaskExecutionDao(): AgentTaskExecutionDao
    abstract fun voiceTaskDao(): VoiceTaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "openclaw_encrypted_local.db"
                ).fallbackToDestructiveMigration()

                // Attach SQLCipher Encrypted SQLite OpenHelper Factory
                val factory = EncryptedDatabaseFactory.createFactory(context.applicationContext)
                if (factory != null) {
                    builder.openHelperFactory(factory)
                }

                val instance = builder.build()
                INSTANCE = instance
                instance
            }
        }
    }
}
