# API Documentation: taika-swarm

This document defines the public API for triggering multi-agent orchestration within the Taika Swarm ecosystem.

## Component: SwarmOrchestrator
The primary service used by external modules (like `taika-cli`) to start and monitor complex, iterative agent workflows.

* **Method:** `public SwarmResult orchestrate(Path projectPath, String mainObjective)`
    * **Input:**
        * `projectPath` (Path): The absolute path to the root of the target project to be modified/analyzed.
        * `mainObjective` (String): The high-level objective of the operation (e.g., "Add an authentication service and ensure 100% test coverage").
    * **Output:** A `SwarmResult` DTO containing the final outcome of the entire execution chain.

### Supporting Data Structures

#### DTO: `SwarmResult`
Represents the final state of the entire orchestration thread.
* `String threadId`: Unique identifier for the execution history.
* `SwarmStatus status`: The final status of the chain (`SUCCESS`, `FAILURE`, `MAX_ITERATIONS_REACHED`).
* `List<AgentInvocationLog> invocationHistory`: A chronological list of which agents were run, what inputs they received, and what they returned.
* `String finalSummary`: A high-level explanation of the final result.

#### DTO: `AgentInvocationLog`
* `AgentType type`: The role assumed (`CODER`, `TESTER`, etc.).
* `LocalDateTime startTime`: Timestamp when the agent was kicked off.
* `LocalDateTime endTime`: Timestamp when the agent completed.
* `AgentStatus status`: Outcome of this specific step.