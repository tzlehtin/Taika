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
     * Responsible for generating Robot Framework acceptance and process-level test suites (*.robot).
     */
    ROBOT_TESTER,
    /**
     * Responsible for generating unit tests (e.g., JUnit or Vitest) based on specifications.
     */
    UNIT_TESTER,
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
    VALIDATOR,
    /**
     * Responsible for deep architectural, code quality, and test effectiveness auditing. Generates task.md and quality reports.
     */
    AUDITOR
}