package dev.taika.api;

/**
 * Defines the rigid operational roles and structural boundaries assigned to swarm nodes during execution.
 */
public enum AgentType {
    /**
     * Responsible for human-to-swarm requirements translation and drafting/modifying the target project's `api.md`.
     */
    INTERFACE_DESIGNER,
    /**
     * Responsible for translating specifications into production-ready Java code under `src/main/java`. Forbidden from touching tests.
     */
    CODER,
    /**
     * Responsible for writing and maintaining JUnit test classes under `src/test/java` to satisfy code coverage thresholds.
     */
    TESTER,
    /**
     * Responsible for guarding the quality gate. Parses compiler logs and coverage metrics to issue precise change loops.
     */
    VALIDATOR
}