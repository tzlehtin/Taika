# taika-agent

The Gemini LLM integration layer for Taika. It translates system instructions and context into structured code modifications.

## Core Responsibilities
* Manages localized prompt templates (system instructions/law books) for specific agent roles.
* Integrates with Gemini models via production-proven Spring AI Vertex AI starters.

## Technical Alignment
Aligns strictly with production standards by using explicit Spring AI Vertex AI model starters:
* `spring-ai-starter-model-vertex-ai-gemini`
* `spring-ai-starter-model-vertex-ai-embedding`

## Constraints
* Must enforce strict JSON schemas using Spring AI's structured output converters to guarantee deterministic code file updates.