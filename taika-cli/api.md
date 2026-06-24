# API Documentation: taika-cli

This document defines the interface and contract for the Command Line Interface of the Taika ecosystem. Since this is an executable CLI application, its "public API" is defined primarily through its command-line interface syntax and exit codes.

## CLI Command Interface

### Command: `taika execution`
Triggers the multi-agent swarm orchestration for a targeted local repository.

* **Arguments & Flags:**
    * `--path, -p` (Required, String): The absolute or relative path to the root directory of the project to be modified/analyzed.
    * `--objective, -o` (Required, String): The high-level instruction or business problem that the swarm needs to solve.
* **Example Usage:**
  ```bash
  taika execution --path /users/projects/my-app --objective "Add validation to user registration endpoint and verify with tests"