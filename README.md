# Taika 🧠⏳

**Taika** (Finnish for *"Magic"*) is a hyper-lightweight, open-source AI development swarm built with Spring Boot for Linux environments. 

Instead of relying on unstable "vibe coding" or endless prompt spirals, Taika brings absolute system engineering discipline to AI-driven development. It breaks your architecture into highly isolated, modular "miniature worlds" (Maven modules) that strictly fit into the AI's context window.

### Key Features:
* **The Compilation Loop:** Automatically runs `mvn clean compile` and `mvn test` natively via Linux process tools.
* **Deterministic Guardrails:** Captures raw Maven error logs and Surefire reports, feeding them directly back to Gemini agents until the code compiles perfectly.
* **Hyper-Modular Architecture:** Split into 5 clean Spring modules (`tools`, `api`, `agent`, `swarm`, `cli`) leveraging Spring Shell and Spring AI.
