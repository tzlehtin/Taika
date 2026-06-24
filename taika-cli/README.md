# `taika-cli` Module

## Responsibility

This module is the user-facing entry point for the entire Taika system. It provides a clean, interactive command-line interface (CLI) built with **Spring Shell**. Its sole purpose is to accept user commands and delegate the execution to the `taika-swarm` module.

## Core Component

*   **`SwarmCommands`**: A `@ShellComponent` that defines all user-callable commands.

## Key Command: `execution`

This is the primary command to start the agent swarm.

*   **Usage**: `execution --path <project-path> --objective <your-goal>`
*   **`--path` (`-p`)**: The absolute or relative path to the target project directory.
*   **`--objective` (`-o`)**: A high-level, natural language description of the task the swarm needs to accomplish.

## Dependencies
*   `org.springframework.shell:spring-shell-starter`: Provides the core CLI framework.
*   `dev.taika:taika-swarm`: The `SwarmCommands` class directly calls the `SwarmOrchestrator` from this module to initiate the process.