# Module Context: taika-swarm

## Responsibility
The core orchestration engine and state-machine of Taika. It coordinates different agent roles (e.g., Interface Agent, Logic Agent, Test Agent) and executes the persistent error-correction loop.

## Internal Swarm Dependencies
This module glues the system together and depends on:
* `dev.taika:taika-api`
* `dev.taika:taika-tools`
* `dev.taika:taika-agent`

## Execution Flow
1. Triggers `taika-tools` to compile the codebase.
2. If compilation fails, captures the raw error log via `taika-api` structures.
3. Routes the error context back to the responsible `taika-agent` instance.
4. Loops continuously until `mvn clean compile` reports 100% success.