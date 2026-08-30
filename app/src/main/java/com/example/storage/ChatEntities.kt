package com.example.storage

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastMessage: String = "",
    val modelUsed: String = "gemini-3.5-flash",
    val providerId: String = "gemini",
    val messageCount: Int = 0,
    val totalTokens: Int = 0,
    val isPinned: Boolean = false,
    val tagsJson: String = "[]",
    val monthPartition: String = ""
) {
    fun toDomain(): ConversationSummary {
        val tagsList = mutableListOf<String>()
        try {
            val array = JSONArray(tagsJson)
            for (i in 0 until array.length()) {
                tagsList.add(array.getString(i))
            }
        } catch (_: Exception) {}

        return ConversationSummary(
            id = id,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastMessage = lastMessage,
            modelUsed = modelUsed,
            providerId = providerId,
            messageCount = messageCount,
            totalTokens = totalTokens,
            isPinned = isPinned,
            tags = tagsList,
            monthPartition = monthPartition
        )
    }

    companion object {
        fun fromDomain(domain: ConversationSummary): ConversationEntity {
            val array = JSONArray()
            domain.tags.forEach { array.put(it) }
            return ConversationEntity(
                id = domain.id,
                title = domain.title,
                createdAt = domain.createdAt,
                updatedAt = domain.updatedAt,
                lastMessage = domain.lastMessage,
                modelUsed = domain.modelUsed,
                providerId = domain.providerId,
                messageCount = domain.messageCount,
                totalTokens = domain.totalTokens,
                isPinned = domain.isPinned,
                tagsJson = array.toString(),
                monthPartition = domain.monthPartition
            )
        }
    }
}

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversationId"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val thinkingReasoning: String? = null,
    val toolCallsJson: String = "[]",
    val timestamp: Long = System.currentTimeMillis(),
    val tokenCount: Int = 0,
    val monthPartition: String = ""
) {
    fun toDomain(): Message {
        val toolCallsList = mutableListOf<ToolCall>()
        try {
            val tcArray = JSONArray(toolCallsJson)
            for (i in 0 until tcArray.length()) {
                toolCallsList.add(ToolCall.fromJson(tcArray.getJSONObject(i)))
            }
        } catch (_: Exception) {}

        return Message(
            id = id,
            conversationId = conversationId,
            role = role,
            content = content,
            thinkingReasoning = thinkingReasoning,
            toolCalls = toolCallsList,
            timestamp = timestamp,
            tokenCount = tokenCount,
            monthPartition = monthPartition
        )
    }

    companion object {
        fun fromDomain(domain: Message): MessageEntity {
            val toolCallsArray = JSONArray()
            domain.toolCalls.forEach { toolCallsArray.put(it.toJson()) }

            return MessageEntity(
                id = domain.id,
                conversationId = domain.conversationId,
                role = domain.role,
                content = domain.content,
                thinkingReasoning = domain.thinkingReasoning,
                toolCallsJson = toolCallsArray.toString(),
                timestamp = domain.timestamp,
                tokenCount = domain.tokenCount,
                monthPartition = domain.monthPartition
            )
        }
    }
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllConversationsFlow(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getAllConversations(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<ConversationEntity>)

    @Query("UPDATE conversations SET isPinned = :isPinned WHERE id = :id")
    suspend fun updatePinnedState(id: String, isPinned: Boolean)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: String)

    @Query("DELETE FROM conversations")
    suspend fun clearAllConversations()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversationFlow(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesForConversation(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()
}
