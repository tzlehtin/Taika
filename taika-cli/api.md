# API Documentation: taika-cli

This document defines the interface and contract for the Command Line Interface of the Taika ecosystem. Since this is an executable CLI application, its "public API" is defined primarily through its command-line interface syntax and exit codes.

## CLI Command Interface

### Command: `taika audit`
Performs a comprehensive quality, architectural, and test effectiveness audit of the target repository. Generates `taika-report-audit.html` and creates `task.md` outlining concrete code, architecture, and test fixes.

* **Arguments & Flags:**
    * `--path, -p` (Required, String): Path to the target project root.
* **Example Usage:**
  ```bash
  taika audit --path /home/tero/cases/lumo-auth-service
  ```

### Command: `taika execution`
Triggers full multi-agent swarm orchestration for a targeted local repository.

* **Arguments & Flags:**
    * `--path, -p` (Required, String): Path to the target project root.
    * `--objective, -o` (Required, String): High-level feature request or objective.
* **Example Usage:**
  ```bash
  taika execution --path /home/tero/cases/lumo-auth-service --objective "Implement user validation"
  ```

### Command: `taika gen-code` (alias: `code`)
Executes the deterministic TDD code generation workflow (JUnit/Vitest generation -> Dummy stubs with TODO -> Test verification -> Full implementation -> Final report).

* **Arguments & Flags:**
    * `--path, -p` (Required, String): The absolute or relative path to the root directory of the project to be modified/analyzed.
    * `--max-loops, -m` (Optional, Integer, Default: 8): Maximum compile/test self-healing cycles per phase.
    * `--objective, -o` (Optional, String): Custom objective or feature requirement.
* **Example Usage:**
  ```bash
  taika gen-code --path /home/tero/cases/lumo-auth-service --max-loops 3
  ```

### Command: `taika gen-robots` (alias: `robot-tests`)
Generates acceptance- and process-level Robot Framework test suites (*.robot) from `context.md` and `api.md` without triggering production code generation.

* **Arguments & Flags:**
    * `--path, -p` (Required, String): Path to the target project root containing specification files.
    * `--max-loops, -m` (Optional, Integer, Default: 8): Maximum iterative self-correction cycles with `robot --dryrun`.
* **Example Usage:**
  ```bash
  taika gen-robots --path /home/tero/cases/lumo-auth-service
  ```