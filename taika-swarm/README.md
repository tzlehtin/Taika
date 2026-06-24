# `taika-swarm` Module

## Responsibility

This module is the heart of the Taika system. It acts as the central orchestrator, managing a stateful, multi-agent workflow to achieve a high-level development objective. Its primary responsibility is to guide a sequence of specialized agents (`CODER`, `TESTER`, `VALIDATOR`) through a persistent compilation and error-correction loop until the task is successfully completed.

## Core Component

*   **`SwarmOrchestrator`**: The main service that implements the orchestration logic. It receives a high-level objective and manages the entire agent execution chain.

## Architectural Principles & Execution Flow

The `SwarmOrchestrator` operates as a state machine with a built-in feedback loop:

1.  **Initiation**: The process starts when `orchestrate()` is called with a project path and a main objective. The first agent to be invoked is always the `CODER`.
2.  **State Transitions**: After an agent successfully completes its task (returns a `SUCCESS` status), the orchestrator determines the next agent in the chain (`CODER` -> `TESTER` -> `VALIDATOR`) and formulates a new, specific task for it.
3.  **Self-Correction Loop**: If any agent in the chain fails, the orchestrator does not give up. Instead, it captures the failure summary and execution log, formulates a new corrective task, and routes the work back to the `CODER` agent. This creates a robust, self-healing development cycle.
4.  **Completion & Safety**: The orchestration is considered successful only when the `VALIDATOR` agent completes its task successfully. To prevent infinite loops, the entire process will time out and fail if a solution is not reached within a predefined maximum number of iterations (`MAX_ITERATIONS`).

## Dependencies
*   `dev.taika:taika-agent`: The `SwarmOrchestrator` depends on the `AgentService` to execute individual, specialized agent tasks.
*   `dev.taika:taika-api`: Uses shared data structures like `AgentType` and `AgentResult` to communicate with the agent layer.