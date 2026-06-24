# Module Context: taika-swarm

## Responsibility
This module acts as the orchestrator and conductor of the Taika system. It manages the multi-agent execution loops ("threads"), coordinates agent handoffs, and decides the next steps based on intermediate outcomes. It has no direct knowledge of how an individual agent works internally; it only orchestrates their lifecycles and chains their execution.

## Dependencies
* `dev.taika:taika-agent`: Used to instantiate and invoke specific agent profiles (CODER, TESTER, VALIDATOR).
* `dev.taika:taika-api`: Provides shared data structures, such as `AgentType`, `AgentResult`, and communication DTOs.

## Architectural Blueprint
The swarm orchestrator executes tasks by managing agent loops and state transitions within a dynamic context.

1.  **Command Reception**: The swarm receives an orchestration request from `taika-cli` via its public API, specifying the target project path and the overarching objective.
2.  **Thread Initiation**: The swarm initializes an execution context (a "Thread"). In the initial phase, this thread executes tasks sequentially by chaining agents.
3.  **The Chaining & Decision Loop**:
    * The swarm invokes a `CODER` agent to implement a feature or fix a bug based on the task description.
    * Once the `CODER` returns a result, the swarm analyzes the outcome and automatically triggers a `TESTER` agent to verify the changes.
    * If the `TESTER` fails (e.g., compilation error or broken test), the swarm intelligently routes the failure logs *back* to a new `CODER` agent instance, providing it with the exact error context for iterative self-healing.
    * Once tests pass, a `VALIDATOR` agent can be invoked to ensure compliance with architecture and quality rules.
4.  **Future Scalability**: The architecture must allow transitioning from sequential chaining to concurrent, parallel agent execution without breaking the public API contract.

## Core Components
* **`SwarmOrchestrator`**: The main entry point service that manages execution threads.
* **`ExecutionThread`**: A stateful model representing a single end-to-end task execution history, holding the logs, artifact states, and agent transition markers.