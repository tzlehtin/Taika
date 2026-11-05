package dev.taika.api;

import java.util.List;

/**
 * An immutable DTO (Java Record) that represents the final output of an agent's execution sequence.
 *
 * @param taskId           Unique identifier of the executed orchestration task.
 * @param status           The outcome state of the execution (e.g., "SUCCESS", "FAILURE", "RETRY_REQUIRED").
 * @param summary          A high-level explanation of what the agent achieved or where it failed.
 * @param modifiedFiles    Absolute or relative paths of files targeted and altered during the execution.
 * @param executionLog     Captured stdout/stderr or internal thought loops generated during the tool execution cycle.
 * @param promptTokens     Number of prompt tokens consumed.
 * @param generationTokens Number of generation/completion tokens consumed.
 * @param totalTokens      Total tokens consumed.
 * @param reasoningTokens  Number of internal reasoning/thinking tokens consumed.
 */
public record AgentResult(
        String taskId,
        String status,
        String summary,
        List<String> modifiedFiles,
        String executionLog,
        long promptTokens,
        long generationTokens,
        long totalTokens,
        long reasoningTokens
) {
    public AgentResult(String taskId, String status, String summary, List<String> modifiedFiles, String executionLog) {
        this(taskId, status, summary, modifiedFiles, executionLog, 0L, 0L, 0L, 0L);
    }

    public AgentResult(String taskId, String status, String summary, List<String> modifiedFiles, String executionLog,
                       long promptTokens, long generationTokens, long totalTokens) {
        this(taskId, status, summary, modifiedFiles, executionLog, promptTokens, generationTokens, totalTokens,
                Math.max(0L, totalTokens - (promptTokens + generationTokens)));
    }
}