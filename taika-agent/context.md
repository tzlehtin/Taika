# Module Context: taika-agent

## Responsibility
The Gemini LLM integration layer. It manages the prompt templates (system instructions/law books) for specific agent roles and handles Structured Outputs.

## Approved Dependency Setup
This module must align strictly with the existing production-proven approach from lumo.ws, utilizing Spring AI starters for Google Vertex AI. 

Required dependencies to be defined with correct, explicit versions (to avoid root BOM version resolution issues):
* `org.springframework.ai:spring-ai-starter-model-vertex-ai-gemini`
* `org.springframework.ai:spring-ai-starter-model-vertex-ai-embedding`

## AI Constraints
* Must interact with Gemini models via Spring AI abstractions (`ChatModel`, `ChatResponse`).
* Must enforce strict JSON schemas using Spring AI's structured output converters so generated files can be written back to the file system deterministically.