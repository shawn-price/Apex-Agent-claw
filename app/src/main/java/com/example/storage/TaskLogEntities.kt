package com.example.storage

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "agent_task_executions")
data class AgentTaskExecutionEntity(
    @PrimaryKey val id: String,
    val taskId: String? = null,
    val title: String,
    val prompt: String,
    val status: String = "pending",
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val durationMs: Long = 0L,
    val modelUsed: String = "gemini-3.5-flash",
    val triggerType: String = "Cron Schedule",
    val toolCallsCount: Int = 0,
    val outputSummary: String? = null,
    val errorMessage: String? = null,
    val progressPercent: Float = 0f,
    val currentStep: String? = null,
    val cancellationReason: String? = null,
    val logsJson: String = "[]"
) {
    fun toDomain(): AgentTaskExecution {
        val logsList = mutableListOf<TaskLogEntry>()
        try {
            val logsArray = JSONArray(logsJson)
            for (i in 0 until logsArray.length()) {
                logsList.add(TaskLogEntry.fromJson(logsArray.getJSONObject(i)))
            }
        } catch (_: Exception) {}

        return AgentTaskExecution(
            id = id,
            taskId = taskId,
            title = title,
            prompt = prompt,
            status = status,
            startedAt = startedAt,
            finishedAt = finishedAt,
            durationMs = durationMs,
            modelUsed = modelUsed,
            triggerType = triggerType,
            toolCallsCount = toolCallsCount,
            outputSummary = outputSummary,
            errorMessage = errorMessage,
            progressPercent = progressPercent,
            currentStep = currentStep,
            cancellationReason = cancellationReason,
            logs = logsList
        )
    }

    companion object {
        fun fromDomain(domain: AgentTaskExecution): AgentTaskExecutionEntity {
            val logsArray = JSONArray()
            domain.logs.forEach { logsArray.put(it.toJson()) }

            return AgentTaskExecutionEntity(
                id = domain.id,
                taskId = domain.taskId,
                title = domain.title,
                prompt = domain.prompt,
                status = domain.status,
                startedAt = domain.startedAt,
                finishedAt = domain.finishedAt,
                durationMs = domain.durationMs,
                modelUsed = domain.modelUsed,
                triggerType = domain.triggerType,
                toolCallsCount = domain.toolCallsCount,
                outputSummary = domain.outputSummary,
                errorMessage = domain.errorMessage,
                progressPercent = domain.progressPercent,
                currentStep = domain.currentStep,
                cancellationReason = domain.cancellationReason,
                logsJson = logsArray.toString()
            )
        }
    }
}

@Dao
interface AgentTaskExecutionDao {
    @Query("SELECT * FROM agent_task_executions ORDER BY startedAt DESC")
    fun getAllTaskExecutionsFlow(): Flow<List<AgentTaskExecutionEntity>>

    @Query("SELECT * FROM agent_task_executions ORDER BY startedAt DESC")
    suspend fun getAllTaskExecutions(): List<AgentTaskExecutionEntity>

    @Query("SELECT * FROM agent_task_executions WHERE id = :id LIMIT 1")
    suspend fun getTaskExecutionById(id: String): AgentTaskExecutionEntity?

    @Query("SELECT * FROM agent_task_executions WHERE taskId = :taskId ORDER BY startedAt DESC")
    fun getExecutionsForTaskFlow(taskId: String): Flow<List<AgentTaskExecutionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTaskExecution(execution: AgentTaskExecutionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskExecutions(executions: List<AgentTaskExecutionEntity>)

    @Query("DELETE FROM agent_task_executions WHERE id = :id")
    suspend fun deleteTaskExecutionById(id: String)

    @Query("DELETE FROM agent_task_executions")
    suspend fun clearAllTaskExecutions()
}
