# API Documentation: taika-api

This document acts as the global type registry and immutable contract sheet for all Taika modules.

## Global Enum: AgentType
Defines the rigid operational roles and structural boundaries assigned to swarm nodes during execution.

*   `INTERFACE_DESIGNER`: Responsible for human-to-swarm requirements translation and drafting/modifying the target project's `api.md`.
*   `CODER`: Responsible for translating specifications into production-ready Java code under `src/main/java`. Forbidden from touching tests.
*   `TESTER`: Responsible for writing and maintaining JUnit test classes under `src/test/java` to satisfy code coverage thresholds.
*   `VALIDATOR`: Responsible for guarding the quality gate. Parses compiler logs and coverage metrics to issue precise change loops.

## Data Structure: AgentResult
An immutable DTO (Java Record) that represents the final output of an agent's execution sequence.

*   **Fields:**
    *   `String taskId`: Unique identifier of the executed orchestration task.
    *   `String status`: The outcome state of the execution (e.g., `SUCCESS`, `FAILURE`, `RETRY_REQUIRED`).
    *   `String summary`: A high-level explanation of what the agent achieved or where it failed.
    *   `List<String> modifiedFiles`: Absolute or relative paths of files targeted and altered during the execution.
    *   `String executionLog`: Captured stdout/stderr or internal thought loops generated during the tool execution cycle.