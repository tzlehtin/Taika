# taika-swarm

The core orchestration engine and state-machine of Taika. It acts as the "brains" that manages the swarm.

## Core Responsibilities
* Coordinates and instantiates active agent roles (e.g., Interface Agent, Logic Agent, Test Agent).
* Executes the persistent **Compilation & Error-Correction Loop** until `taika-tools` reports a 100% clean build.

## Internal Dependencies
* `dev.taika:taika-api`
* `dev.taika:taika-tools`
* `dev.taika:taika-agent`