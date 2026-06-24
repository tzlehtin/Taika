package dev.taika.swarm.orchestration.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents the state and history of a single end-to-end task execution.
 */
public class ExecutionThread {

    private final String id = UUID.randomUUID().toString();
    private final List<AgentInvocationLog> history = new ArrayList<>();

    public void log(AgentInvocationLog log) {
        this.history.add(log);
    }

    public List<AgentInvocationLog> getHistory() {
        return List.copyOf(history);
    }

    public String getId() { return id; }
}