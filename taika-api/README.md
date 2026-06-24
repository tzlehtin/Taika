# taika-api

The shared contract and communications layer for the Taika swarm.

## Core Responsibilities
* Defines immutable data structures (Java Records/POJOs) passed between agents.
* Manages core system events (e.g., `CodeChangeRequest`, `CompilationErrorEvent`).

## Constraints
* **Pure Logic Only:** Absolutely no AI dependencies or OS/Process execution logic are allowed here.