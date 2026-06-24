# taika-tools

This module handles low-level Linux OS capabilities. It acts as the physical "hands" of the Taika swarm.

## Core Responsibilities
* Natively executes terminal commands using Java's `ProcessBuilder`.
* Focuses strictly on `mvn clean compile` and `mvn test` execution.
* Captures raw `stdout`/`stderr` streams and Surefire XML reports, formatting them into clean strings for agent analysis.

## Constraints
* **Absolute AI Isolation:** This module has zero knowledge of LLMs, prompts, or Spring AI APIs.