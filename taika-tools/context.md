# Module Context: taika-tools

## Responsibility
This module handles low-level Linux OS capabilities. It acts as the "hands" of the swarm. It has zero knowledge of AI logic or LLM APIs.

## Dependencies
* `org.springframework.boot:spring-boot-starter` (Inherited from parent)

## Architectural Blueprint
1. Must use Java's native `ProcessBuilder` to execute terminal commands.
2. Must target native Linux environments.
3. Primary component to build next: `MavenExecutor`. It must take a target project `Path`, execute `mvn clean compile` and `mvn test`, and capture raw `stdout`/`stderr` streams and Surefire XML reports into clean strings for the swarm to analyze.