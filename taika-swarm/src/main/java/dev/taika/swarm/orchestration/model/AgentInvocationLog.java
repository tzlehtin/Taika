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
 */
public record AgentInvocationLog(AgentType type, String status, LocalDateTime startTime, LocalDateTime endTime) {
    public AgentInvocationLog(AgentType type, String status) {
        this(type, status, LocalDateTime.now(), LocalDateTime.now());
    }
}