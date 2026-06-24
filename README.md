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