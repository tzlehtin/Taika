# `taika-agent` Module

## Responsibility

This module is the "brain" of the Taika system. It is responsible for orchestrating the AI-driven development process. It integrates with Google's Vertex AI Gemini model via the **Spring AI** framework and has no direct access to the file system or OS processes. For all physical actions, it must operate exclusively through the services provided by the `taika-tools` module.

## Core Components

*   **`AgentService`**: The public-facing entry point for the module. It receives a task, assembles the full context (including project files read via `FileSystemReader`), constructs a detailed prompt for the AI, and initiates the execution loop.
*   **`ToolConfiguration`**: This is the central component for exposing tools to the AI. It defines every available service from `taika-tools` as a standard Spring `Function` bean. Each bean is given a clear, natural language `@Description` that the AI uses to understand its purpose.

## Architectural Principles & Execution Flow

1.  **Task Initiation**: An external caller (like `taika-swarm` or `taika-cli`) invokes `AgentService.executeTask` with a project path and a task description.
2.  **Context Loading**: `AgentService` uses the `fileSystemReader` tool to load the project's source code and markdown files into the AI's context.
3.  **Tool Registration via Spring AI**: The `AgentService` calls the `ChatClient` and specifies the names of the tool beans it wants to make available (e.g., `.tools("fileSystemReader", "fileSystemWriter", ...)`).
4.  **Function Calling**: Spring AI automatically finds the corresponding `Function` beans from `ToolConfiguration`, generates a JSON schema for the AI model from their DTO inputs (e.g., `FileSystemReader.ReadRequest`), and includes these schemas in the prompt.
5.  **Execution**: When the AI model decides to use a tool, Spring AI intercepts the request, invokes the correct `Function` bean with the AI-provided arguments, and returns the result to the model.

This architecture ensures a clean separation of concerns:
*   `taika-tools` knows nothing about AI.
*   `taika-agent` knows nothing about the low-level implementation of the tools; it only knows them by the names defined in `ToolConfiguration`.

## Dependencies
*   `org.springframework.ai:spring-ai-vertex-ai-gemini-starter`: For core `ChatClient` integration.
*   `dev.taika:taika-tools`: Provides the underlying tool implementations.
*   `dev.taika:taika-api`: Provides shared data structures like `AgentType` and `AgentResult`.