# Module Context: taika-api

## Responsibility
This is the shared contract and communications layer. It defines the immutable data structures (DTOs) and events passed between agents. 

## Constraints
* Absolute isolation: **No** AI dependencies, **No** OS/Process execution logic.
* Contains only pure Java Records/POJOs representing system state and messages (e.g., `CodeChangeRequest`, `CompilationErrorEvent`).