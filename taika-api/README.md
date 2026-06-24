# `taika-api` Module

This module is the foundational contract layer for the entire Taika project.

## Responsibility

The `taika-api` module's sole responsibility is to provide the shared, immutable data structures (domain models, DTOs) used for communication between all other modules in the Taika ecosystem. It contains **no business logic**.

This ensures that all components, from the low-level `taika-tools` to the high-level `taika-agent`, operate on a common, stable, and well-defined set of types.

## Core Components

This module provides the following key data structures:

*   **`AgentType` (Enum):** Defines the rigid operational roles an agent can assume (`CODER`, `TESTER`, `VALIDATOR`, etc.). This acts as a global registry for agent specializations.
*   **`AgentResult` (Record):** An immutable Data Transfer Object (DTO) used to represent the final outcome of an agent's task execution. It includes the status, a summary, and a log of actions taken.

## Architectural Principles

*   **Immutability:** All data structures in this module are designed to be immutable (using Java Records where possible) to ensure thread-safe state sharing across the system.
*   **Zero Logic:** This module is purely for data definitions. All logic resides in other modules.
*   **Build Order:** As the foundational contract layer, this module must be compiled first in the Maven reactor build.