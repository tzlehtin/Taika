# Module Context: taika-api

## Responsibility
This module is the single source of truth for the shared domain models, data transfer objects (DTOs), and configuration constants used across the entire Taika ecosystem. It contains no business logic, no AI prompt orchestration, and no external tool integration. Its sole purpose is to provide standard, immutable types so that all other miniature worlds (modules) can communicate without binary leakage or structural misalignment.

## Dependencies
*   `org.springframework.boot:spring-boot-starter`: Provides the baseline Spring context infrastructure if needed, though this module consists primarily of pure Java types (Enums, Records, or POJOs).

## Architectural Blueprint
As the foundational contract layer, `taika-api` must be compiled first in the reactor build order. Any change to the system's global roles or result structures must be applied here within the "Slow Thinking" design phase before any logic generation begins.

All data structures defined here should lean towards immutability (e.g., Java Records) to guarantee thread-safe state distribution across the asynchronous components of the swarm.