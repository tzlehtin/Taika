# Taika 🧠⏳

**Taika** (Finnish for *"Magic"*) is a hyper-lightweight, open-source AI development swarm orchestrator built with Spring Boot 3.3.1 for Linux.

Instead of relying on unstable "vibe coding" or endless prompt spirals, Taika brings absolute system engineering discipline to AI-driven development. It breaks your architecture into highly isolated, modular "miniature worlds" (Maven modules) that strictly fit into the AI's context window.

## License
Licensed under the [GNU General Public License v3.0 (GPLv3)](LICENSE).

## Multi-Module Architecture
The project is split into 5 strictly decoupled sub-modules. Click on any module to view its specific documentation and responsibilities:

* [**`taika-tools`**](./taika-tools/README.md) — Low-level Linux process execution (`ProcessBuilder`) and Maven/JUnit log parsing.
* [**`taika-api`**](./taika-api/README.md) — Shared DTOs, events, and communication contracts. Fully isolated from AI and OS logic.
* [**`taika-agent`**](./taika-agent/README.md) — Gemini LLM integration layer leveraging production-proven Spring AI Vertex AI starters.
* [**`taika-swarm`**](./taika-swarm/README.md) — Core state-machine, agent role coordination, and the persistent compilation error-correction loop.
* [**`taika-cli`**](./taika-cli/README.md) — Native Linux terminal command-line interface built with Spring Shell.

---

## Preparing a Target Project (The "Miniature World")

Taika operates on a target project that must follow a specific contract-first structure. This structure allows the AI agents to understand the project's architecture, dependencies, and constraints completely within their limited context window.

### 1. Maven Project Structure
The target project must be a standard Maven project. It is highly recommended to break your codebase into independent, decoupled Maven modules. This modular approach is the core principle of Taika, creating tightly focused "microworlds" for the autonomous agents to operate in.

### 2. Module Definition Files
Each target module you want the Taika swarm to modify or operate on must contain the following three specification files in its root directory:
* `README.md` — A standard description explaining the module's high-level business purpose.
* `context.md` — Describes the module's technical responsibilities, strict architectural rules, and core components. It sets the immutable "rules of the world" for the AI.
* `api.md` — Defines the public API or communication contract (e.g., REST endpoints, public services, methods, or DTO interfaces).

> **Tip:** You can design these blueprint files yourself or use an AI chat assistant to help you draft them. For a production reference, examine the structure of Taika's own modules (e.g., `taika-tools/context.md`).

### Brief Guide to Specification Formats

#### `context.md` (The "Why")
* **Responsibility**: A single, declarative paragraph stating exactly what the module does.
* **Architectural Principles**: High-level constraints (e.g., *"This module must remain database-agnostic"* or *"All business logic must reside within service beans"*).
* **Core Components**: A strict list of main classes, interfaces, and their technical scope.

#### `api.md` (The "What")
* **Public Interface**: A clean enumeration of public endpoints, commands, or methods.
* **Inputs & Outputs**: Strictly typed parameters, payload formats, and explicit return types.
* **Data Contracts**: Explicit definitions of DTO structures participating in the public contract.

By defining these boundaries upfront, you provide the Taika swarm with a deterministic boundary to generate, verify, and validate code autonomously.

---

## Getting Started

### Prerequisites
* Java 17 or higher
* Maven 3.9+
* Linux Environment

### How to Build
To verify and compile the entire deterministic multi-module setup, run:
```bash
mvn clean install -U
```

### How to Run

1.  **Authenticate with Google Cloud**: Before running the application, you must authenticate your local environment to use Google Cloud services. Run the following command and follow the instructions in your browser:
    ```bash
    gcloud auth application-default login
    ```

2.  **Configure Project ID**: Open the `taika-cli/src/main/resources/application.properties` file and replace `your-gcp-project-id` with your actual Google Cloud Project ID.

3.  **Run the Application**: Execute the CLI application from the project root directory:
    ```bash
    java -jar taika-cli/target/taika-cli-0.0.1-SNAPSHOT.jar
    ```

4.  **Execute a Task**: Once the Spring Shell prompt (`shell:>` aukeaa) appears, you can trigger the agent swarm with the `execution` command:
    ```bash
    execution --path /path/to/your/target/project --objective "Implement a new REST endpoint that returns a list of users."
    ```