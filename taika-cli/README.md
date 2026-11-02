# `taika-cli` Module

## Responsibility

This module is the user-facing entry point for the entire Taika system. It provides a clean, interactive command-line interface (CLI) built with **Spring Shell**. Its sole purpose is to accept user commands and delegate the execution to the `taika-swarm` module.

## Core Component

*   **`SwarmCommands`**: A `@ShellComponent` that defines all user-callable commands.

## Supported Commands

*   **`gen-robots`**: Generates declarative acceptance tests using Robot Framework and verifies syntax with `robot --dryrun`.
    *   `gen-robots --path <project-path> --max-loops 3`
*   **`gen-code`**: Runs the 7-step deterministic TDD loop (JUnit/Vitest unit tests -> dummy stubs -> Maven/npm test verification -> production implementation -> final validation).
    *   `gen-code --path <project-path> --max-loops 3`
*   **`audit`**: Performs architectural, code quality, and test effectiveness analysis, outputting `taika-report-audit.html` and generating `task.md` when fixes are required.
    *   `audit --path <project-path>`
    *   `audit --path <project-path> --security`
    *   `audit --path <project-path> --mutation`
*   **`mutation-test`** (alias: `pitest`): Performs mutation testing using PITest (Java) or Stryker (Vue).
    *   `mutation-test --path <project-path>`
*   **`security-scan`** (alias: `owasp-scan`): Performs standalone deep dependency vulnerability scanning (OWASP Dependency-Check / npm audit).
    *   `security-scan --path <project-path>`
*   **`pipeline`** (aliases: `flow`, `auto`): Executes the entire pipeline in sequence (`gen-robots` -> `gen-code` -> `audit`).
    *   `pipeline --path <project-path> --max-loops 3`
    *   `pipeline --path <project-path> --max-loops 3 --security`
    *   `pipeline --path <project-path> --max-loops 3 --mutation`

All commands can be invoked non-interactively directly from the command line using the executable JAR.

## Dependencies
*   `org.springframework.shell:spring-shell-starter`: Provides the core CLI framework.
*   `dev.taika:taika-swarm`: The `SwarmCommands` class directly calls the `SwarmOrchestrator` from this module to initiate the process.