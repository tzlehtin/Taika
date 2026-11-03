package dev.taika.swarm.orchestration.model;

import dev.taika.api.AgentType;

import java.time.LocalDateTime;

/**
 * A record of a single agent invocation within an execution thread.
 *
 * @param type The role the agent assumed.
 * @param status The final status reported by the agent.
 * @param startTime The time the agent was invoked.
 * @param endTime The time the agent completed.
 * @param promptTokens The number of prompt tokens used.
 * @param generationTokens The number of completion/generation tokens used.
 * @param totalTokens The total tokens consumed.
 */
public record AgentInvocationLog(
        AgentType type,
        String status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        long promptTokens,
        long generationTokens,
        long totalTokens
) {
    public AgentInvocationLog(AgentType type, String status) {
        this(type, status, LocalDateTime.now(), LocalDateTime.now(), 0L, 0L, 0L);
    }

    public AgentInvocationLog(AgentType type, String status, LocalDateTime startTime, LocalDateTime endTime) {
        this(type, status, startTime, endTime, 0L, 0L, 0L);
    }

    public AgentInvocationLog(AgentType type, String status, long promptTokens, long generationTokens, long totalTokens) {
        this(type, status, LocalDateTime.now(), LocalDateTime.now(), promptTokens, generationTokens, totalTokens);
    }
}