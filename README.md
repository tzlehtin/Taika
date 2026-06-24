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