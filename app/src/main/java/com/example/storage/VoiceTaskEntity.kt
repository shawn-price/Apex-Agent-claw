package com.example.storage

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

@Entity(tableName = "voice_parsed_tasks")
data class VoiceTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val prompt: String,
    val category: String = "Work", // Work, Personal, Urgent, Automations
    val urgency: String = "MEDIUM", // HIGH, MEDIUM, LOW
    val scheduledTime: String = "Today",
    val duration: String = "30 mins",
    val intervalMinutes: Int = 0,
    val isRecurring: Boolean = false,
    val isCompleted: Boolean = false,
    val isRunning: Boolean = false,
    val rawVoiceTranscript: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toScheduledTask(): ScheduledTask {
        return ScheduledTask(
            id = id,
            title = title,
            prompt = prompt,
            intervalMinutes = intervalMinutes,
            isRecurring = isRecurring,
            isEnabled = !isCompleted,
            lastOutput = "Voice Parsed Task ($category • $urgency)"
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("prompt", prompt)
        put("category", category)
        put("urgency", urgency)
        put("scheduledTime", scheduledTime)
        put("duration", duration)
        put("intervalMinutes", intervalMinutes)
        put("isRecurring", isRecurring)
        put("isCompleted", isCompleted)
        put("isRunning", isRunning)
        put("rawVoiceTranscript", rawVoiceTranscript)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): VoiceTaskEntity = VoiceTaskEntity(
            id = json.optString("id", java.util.UUID.randomUUID().toString()),
            title = json.optString("title", "Voice Moment"),
            prompt = json.optString("prompt", ""),
            category = json.optString("category", "Work"),
            urgency = json.optString("urgency", "MEDIUM"),
            scheduledTime = json.optString("scheduledTime", "Today"),
            duration = json.optString("duration", "30 mins"),
            intervalMinutes = json.optInt("intervalMinutes", 0),
            isRecurring = json.optBoolean("isRecurring", false),
            isCompleted = json.optBoolean("isCompleted", false),
            isRunning = json.optBoolean("isRunning", false),
            rawVoiceTranscript = json.optString("rawVoiceTranscript", ""),
            createdAt = json.optLong("createdAt", System.currentTimeMillis())
        )
    }
}

@Dao
interface VoiceTaskDao {
    @Query("SELECT * FROM voice_parsed_tasks ORDER BY createdAt DESC")
    fun getAllVoiceTasksFlow(): Flow<List<VoiceTaskEntity>>

    @Query("SELECT * FROM voice_parsed_tasks ORDER BY createdAt DESC")
    suspend fun getAllVoiceTasks(): List<VoiceTaskEntity>

    @Query("SELECT * FROM voice_parsed_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): VoiceTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: VoiceTaskEntity)

    @Update
    suspend fun updateTask(task: VoiceTaskEntity)

    @Query("DELETE FROM voice_parsed_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("DELETE FROM voice_parsed_tasks")
    suspend fun clearAllTasks()
}
