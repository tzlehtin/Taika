# `taika-agent` Module

## Responsibility

This module is the "brain" of the Taika system. It is responsible for orchestrating the AI-driven development process. It integrates with Google's Gemini models via the **Spring AI Google GenAI** starter and has no direct access to the file system or OS processes. For all physical actions, it must operate exclusively through the services provided by the `taika-tools` module.

## Core Components

*   **`AgentService`**: The public-facing entry point for the module. It receives a task, assembles the full context (including project files read via `FileSystemReader`), constructs a detailed prompt for the AI, and initiates the execution loop.
*   **`ToolRegistry`**: Exposes services from `taika-tools` to the AI as Spring AI `ToolCallback` instances, dynamically resolving paths against the target project path.

## Architectural Principles & Execution Flow

1.  **Task Initiation**: An external caller (like `taika-swarm` or `taika-cli`) invokes `AgentService.executeTask` with a project path and a task description.
2.  **Context Loading**: `AgentService` uses the `fileSystemReader` tool to load the project's source code and markdown files into the AI's context.
3.  **Tool Registration via Spring AI**: The `AgentService` creates a `ToolRegistry` instance for the specific `projectPath` and provides callbacks via `.toolCallbacks(...)`.
4.  **Function Calling**: Spring AI exposes the tools to Gemini using function calling schemas generated from their input DTOs (e.g., `FileSystemReader.ReadRequest`).
5.  **Execution**: When the AI model decides to use a tool, Spring AI invokes the registered callback, which executes the corresponding `taika-tools` service using safe absolute paths.

This architecture ensures a clean separation of concerns:
*   `taika-tools` knows nothing about AI.
*   `taika-agent` delegates low-level process and file execution to `taika-tools` via `ToolRegistry`.

## Dependencies
*   `org.springframework.ai:spring-ai-starter-model-google-genai`: For core `ChatClient` integration.
*   `dev.taika:taika-tools`: Provides the underlying tool implementations.
*   `dev.taika:taika-api`: Provides shared data structures like `AgentType` and `AgentResult`.