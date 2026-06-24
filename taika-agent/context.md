# Module Context: taika-agent

## Responsibility
This module is the "brain" of the Taika system. It integrates with the Google Vertex AI Gemini API via Spring AI's `ChatClient` to execute structured development tasks. It has zero direct access to the host file system or OS processes; it must act exclusively through the tools exposed by the `taika-tools` module[cite: 2].

## Dependencies
*   `org.springframework.ai:spring-ai-vertex-ai-gemini-starter`: Core Spring AI integration[cite: 2].
*   `dev.taika:taika-tools`: Provides the physical "hands" (file I/O, process execution) via its public API (`../taika-tools/api.md`)[cite: 2].
*   `dev.taika:taika-api`: Shared data structures, DTOs, and the `AgentType` role registry[cite: 2, 3].

## Architectural Swarm Blueprint
The agent engine operates as a stateful, role-based orchestration machine. Instead of generic prompting ("vibe coding"), it dynamically loads a specialized, deterministic sääntökirja (System Instruction / Law Book) based on the requested `AgentType`[cite: 3].

### Execution Flow:
1.  **Task Initiation**: `AgentService` receives an explicit request containing the `projectPath`, target role (`AgentType`), and a `taskDescription`[cite: 3].
2.  **Context Assembly**: The agent uses `taika-tools` capabilities to read `context.md`, `api.md`, and any existing source or test code within the miniature world[cite: 2].
3.  **Role Isolation**: The agent loads a specific external template (e.g., `prompts/CODER.txt`) into the Gemini `ChatClient` system block. This isolates the agent's boundaries, strictly defining what it can and cannot modify.
4.  **Deterministic Function Execution**: The `ToolConfiguration` class defines all available `taika-tools` services as standard Spring `Function` beans. The `AgentService` tells the `ChatClient` which tools to use, and Spring AI automatically discovers and executes these beans when requested by the model. This loops until the contract goal is compiled successfully.

## Core Service Components
*   **`AgentService`**: The public entry point managing orchestration and prompt routing based on the API[cite: 2, 3].
*   **`ToolConfiguration`**: Defines all available tools as Spring `Function` beans, each with a clear description for the AI model.