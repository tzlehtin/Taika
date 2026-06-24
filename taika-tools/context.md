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

*   **`MavenExecutor`**: Executes Maven commands (e.g., `mvn clean install`) within a specified project directory (`Path`). It must capture and return the raw `stdout` and `stderr` streams as a clean `String` for AI analysis, mimicking a terminal's copy-paste output.

*   **`FileSystemReader`**: Recursively scans a given directory (`Path`) to find all files matching specific patterns (e.g., `*.java`, `context.md`). It must read the content of these files and return them, for instance, as a `Map<Path, String>` to preserve their locations.

*   **`FileSystemWriter`**: Provides functionality to write or overwrite file content at a specified `Path`. This allows AI-generated code changes to be physically saved back to the project structure.