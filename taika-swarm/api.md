# API Documentation: taika-swarm

This document defines the public API for triggering multi-agent orchestration within the Taika Swarm ecosystem.

## Component: SwarmOrchestrator
The primary service used by external modules (like `taika-cli`) to start and monitor complex, iterative agent workflows.

* **Method:** `public SwarmResult orchestrate(Path projectPath, String mainObjective)`
    * **Input:**
        * `projectPath` (Path): The absolute path to the root of the target project to be modified/analyzed.
        * `mainObjective` (String): The high-level objective of the operation (e.g., "Add an authentication service and ensure 100% test coverage").
    * **Output:** A `SwarmResult` DTO containing the final outcome of the entire execution chain.
* **Method:** `public SwarmResult generateRobotTests(Path projectPath, int maxLoops)`
    * **Input:**
        * `projectPath` (Path): The target project directory.
        * `maxLoops` (int): Maximum self-healing dry-run iterations.
    * **Output:** A `SwarmResult` confirming generation and dry-run validation of Robot Framework test suites.
* **Method:** `public SwarmResult generateCode(Path projectPath, int maxLoops, String objective)`
    * **Input:**
        * `projectPath` (Path): The target project directory containing specification files.
        * `maxLoops` (int): Maximum compile/test self-healing cycles per phase.
        * `objective` (String): Optional custom goal or requirement.
    * **Output:** A `SwarmResult` DTO confirming completion of the 7-step deterministic TDD workflow (Unit tests + Dummy stubs -> Verification -> Production implementation -> Final validation report). Validates that both unit tests and any existing Robot Framework tests pass in both verification phases.
* **Method:** `public SwarmResult audit(Path projectPath)`
    * **Input:**
        * `projectPath` (Path): The target project directory containing code and specification files.
    * **Output:** A `SwarmResult` DTO confirming completion of the audit, generation of `taika-report-audit.html`, and creation of `task.md`.
* **Method:** `public SwarmResult audit(Path projectPath, boolean scanVulnerabilities)`
    * **Input:**
        * `projectPath` (Path): The target project directory.
        * `scanVulnerabilities` (boolean): Whether to run deep CVE vulnerability checks (OWASP Dependency-Check / npm audit).
* **Method:** `public SwarmResult audit(Path projectPath, boolean scanVulnerabilities, boolean runMutation)`
    * **Input:**
        * `projectPath` (Path): The target project directory.
        * `scanVulnerabilities` (boolean): Whether to run deep CVE vulnerability checks.
        * `runMutation` (boolean): Whether to run mutation testing (PITest / Stryker).
* **Method:** `public String runMutationTesting(Path projectPath)`
    * **Input:**
        * `projectPath` (Path): The target project directory.
    * **Output:** Formatted PITest or Stryker mutation testing results and mutation score.
* **Method:** `public String scanVulnerabilities(Path projectPath)`
    * **Input:**
        * `projectPath` (Path): The target project directory.
    * **Output:** Direct formatted summary of OWASP Dependency-Check and npm audit results.
* **Method:** `public SwarmResult pipeline(Path projectPath, int maxLoops, String objective)`
    * **Input:**
        * `projectPath` (Path): The target project directory containing specification files.
        * `maxLoops` (int): Maximum compile/test self-healing cycles per phase.
        * `objective` (String): Optional custom goal or requirement.
    * **Output:** A `SwarmResult` DTO executing the sequential pipeline (`gen-robots` -> `gen-code` -> `audit`).

### Supporting Data Structures

#### DTO: `SwarmResult`
Represents the final state of the entire orchestration thread.
* `String threadId`: Unique identifier for the execution history.
* `SwarmStatus status`: The final status of the chain (`SUCCESS`, `FAILURE`, `MAX_ITERATIONS_REACHED`).
* `List<AgentInvocationLog> invocationHistory`: A chronological list of which agents were run, what inputs they received, and what they returned.
* `String finalSummary`: A high-level explanation of the final result.

#### DTO: `AgentInvocationLog`
* `AgentType type`: The role assumed (`CODER`, `TESTER`, etc.).
* `LocalDateTime startTime`: Timestamp when the agent was kicked off.
* `LocalDateTime endTime`: Timestamp when the agent completed.
* `AgentStatus status`: Outcome of this specific step.