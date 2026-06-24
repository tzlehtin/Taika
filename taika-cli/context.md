# Module Context: taika-cli

## Responsibility
This module serves as the primary user interface and entry point for the Taika system. Its sole responsibility is to ingest user commands via the Command Line Interface (CLI), validate input parameters, translate those inputs into orchestration objectives, and invoke the execution loop managed by `taika-swarm`. It is a stateless, thin interface layer that handles I/O and user feedback, remaining decoupled from the actual agent orchestration logic.

## Dependencies
* `dev.taika:taika-swarm`: Used to invoke the `SwarmOrchestrator` and trigger multi-agent threads based on user commands.
* `dev.taika:taika-api`: Provides shared core models and data types for processing configuration or common status enums.
* `org.springframework.ai:spring-ai-vertex-ai-gemini-spring-boot-starter`: Required directly at this executable layer to bootstrap the Vertex AI Gemini client and Embedding models.

## Architectural Blueprint
The CLI acts as the deterministic front door to the dynamic agent architecture.

1.  **Command Parsing**: The user executes a command (e.g., `taika execution --path . --objective "Implement feature X"`). The CLI parses the flags and arguments using a standard CLI library.
2.  **Validation**: It verifies that the target project path exists and is a valid directory, and that the main objective is not empty.
3.  **Swarm Invocation**: The CLI bootstraps the context and calls the `SwarmOrchestrator` from `taika-swarm`. It blocks execution (or streams updates if applicable in later phases) while the swarm executes its hybrid agent loops.
4.  **Result Formatting**: Once the swarm completes its execution chain, the CLI receives the `SwarmResult`. It parses the invocation logs and outputs a clean, human-readable report to the terminal (standard output), indicating whether the objective was met and detailing which steps were taken.

## Runtime Configuration & Environment Requirements
Being the executable module, `taika-cli` is responsible for providing the runtime environment configuration for the entire application context (including child modules).

### 1. Application Properties (`src/main/resources/application.properties`)
The following Spring AI configuration keys must be defined at this layer to enable communication with the LLM provider:
* `spring.ai.vertex.ai.gemini.project-id`: The target Google Cloud Project ID (e.g., `lumo-ws`).
* `spring.ai.vertex.ai.gemini.location`: The GCP infrastructure location (e.g., `europe-west1`).
* `spring.ai.vertex.ai.gemini.chat.options.model`: The target Gemini LLM version (e.g., `gemini-2.5-flash`).
* `spring.ai.vertex.ai.gemini.chat.options.temperature`: LLM sampling temperature.
* `spring.ai.vertex.ai.embedding.text.options.model`: The embedding model for semantic processing.

### 2. Authentication Context (Google Cloud Vertex AI)
The application relies on Google Cloud Application Default Credentials (ADC) to authenticate against Vertex AI API. 
* **Prerequisite for local execution**: The user running the CLI must be authenticated locally via the Google Cloud CLI before launching the application.
* **Required Command**:
  ```bash
  gcloud auth application-default login