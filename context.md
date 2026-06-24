# Project Context: Taika (Root Parent)

## Overview
Taika (Finnish for "Magic") is a lightweight, open-source AI swarm orchestrator built with Spring Boot 3.3.1 for Linux. It forces LLMs into a strict, deterministic compilation and error-correction loop, leveraging native Maven compiler and test feedback to guarantee 100% working code.

## License
GNU General Public License v3.0 (GPLv3).

## System Constraints & Multi-Module Architecture
The project is architected into 5 isolated Maven sub-modules to strictly fit within an AI's immediate context window:
1. `taika-tools`: Executes native Linux OS processes (`mvn clean compile`) via `ProcessBuilder` and parses raw logs.
2. `taika-api`: Shared DTOs, events, and communication contracts. No AI or OS logic.
3. `taika-agent`: Gemini LLM integration layer using the official Google Cloud Vertex AI SDK.
4. `taika-swarm`: Core state-machine, role management, and the persistent compilation error-correction loop.
5. `taika-cli`: Native Linux terminal command-line interface built with Spring Shell.

## Current Working Baseline (Parent POM)
The parent POM locks down the environment to Spring Boot 3.3.1 and Java 17. It registers the Spring Milestones repository to allow sub-modules to fetch required pre-releases safely. No global BOMs for AI or Shell are imported at the parent level to prevent unresolvable artifact errors.

## How to Reproduce Build Success
Run the following command from this root directory:
```bash
mvn clean install -U