package dev.taika.swarm.orchestration.model;

import java.util.List;

/**
 * Represents the final state of the entire orchestration thread.
 *
 * @param threadId Unique identifier for the execution history.
 * @param status The final status of the chain.
 * @param invocationHistory A chronological list of agent invocations.
 * @param finalSummary A high-level explanation of the final result.
 */
public record SwarmResult(String threadId, SwarmStatus status, List<AgentInvocationLog> invocationHistory, String finalSummary) {
}