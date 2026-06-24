# Module Context: taika-tools

## Responsibility
This module handles low-level Linux OS capabilities. It acts as the "hands" of the swarm. It has zero knowledge of AI logic or LLM APIs.

## Dependencies
* `org.springframework.boot:spring-boot-starter` (Inherited from parent)

## Architectural Blueprint
1. Must use Java's native `ProcessBuilder` to execute terminal commands.
2. Must target native Linux environments.
3. Must provide a suite of simple, AI-agnostic services for the `taika-swarm` to use.

### Core Service Components
The following Spring `@Service` components must be implemented to provide the physical "hands" for the AI agents:

*   **`MavenExecutor`**: Executes Maven commands. Takes an `ExecuteRequest` DTO containing the project path and goals. It captures and returns the raw `stdout` and `stderr` streams as a clean `String`.

*   **`FileSystemReader`**: Recursively scans for files. Takes a `ReadRequest` DTO containing the root path and glob patterns. It returns a map of file paths and their content.

*   **`FileSystemWriter`**: Writes or overwrites file content. Takes a `WriteRequest` DTO containing the file path and content.

*   **`FileReader`**: Reads a single file. Takes a `ReadFileRequest` DTO containing the file path.