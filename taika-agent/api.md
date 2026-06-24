# API Documentation: taika-agent

This document defines the strict public interface for the contract-driven `taika-agent` module.

## Component: AgentService
Orchestrates a highly specialized AI role to solve a bounded task within a target project path.

* **Method:** `public AgentResult executeTask(Path projectPath, AgentType agentType, String taskDescription)`
    * **Input:**
        * `projectPath` (Path): Absolute directory of the target miniature world (must contain `context.md` and `api.md`).
        * `agentType` (dev.taika.api.AgentType): The rigid operational role assigned to the execution. **Note: This enum is imported from the `taika-api` module.**
        * `taskDescription` (String): The concrete natural language instruction or change request.
    * **Output:** `AgentResult` DTO confirming exit status (`SUCCESS`/`FAILURE`) and execution history.

## External Riippuvuussopimukset (Cross-Module Contracts)

This module depends on the domain structures defined in `dev.taika:taika-api`. For role definitions and behaviors, see `../taika-api/api.md`:
* `AgentType` (`INTERFACE_DESIGNER`, `CODER`, `TESTER`, `VALIDATOR`): Used to dynamically route and load the correct sääntökirja (System Instruction) for the execution loop.