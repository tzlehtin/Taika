package dev.taika.agent.service;

import dev.taika.agent.tools.ToolRegistry;
import dev.taika.api.AgentResult;
import dev.taika.api.AgentType;
import dev.taika.tools.service.FileSystemReader;
import dev.taika.tools.service.FileSystemWriter;
import dev.taika.tools.service.MavenExecutor;
import dev.taika.tools.service.FileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AgentService {

    private static final Logger logger = LoggerFactory.getLogger(AgentService.class);

    private final ChatClient.Builder chatClientBuilder;
    private final FileSystemReader fileSystemReader;
    private final FileSystemWriter fileSystemWriter;
    private final MavenExecutor mavenExecutor;
    private final FileReader fileReader;
    private final String modelName;

    public AgentService(ChatClient.Builder chatClientBuilder,
                        FileSystemReader fileSystemReader,
                        FileSystemWriter fileSystemWriter,
                        MavenExecutor mavenExecutor,
                        FileReader fileReader,
                        @Value("${spring.ai.google.genai.chat.options.model:gemini-3.5-flash}") String modelName) {
        this.chatClientBuilder = chatClientBuilder;
        this.fileSystemReader = fileSystemReader;
        this.fileSystemWriter = fileSystemWriter;
        this.mavenExecutor = mavenExecutor;
        this.fileReader = fileReader;
        this.modelName = modelName;
    }

    public AgentResult executeTask(Path projectPath, AgentType agentType, String taskDescription) {
        logger.info("Executing task '{}' for project '{}' with agent type {}", taskDescription, projectPath, agentType);

        // 1. Context Assembly: Load all relevant project files.
        String context;
        try {
            String[] globPatterns = getGlobPatternsForAgent(agentType);

            logger.info("Assembling context for agent {} with glob patterns: {}", agentType, String.join(", ", globPatterns));
            var readRequest = new FileSystemReader.ReadRequest(projectPath.toAbsolutePath().toString(), globPatterns);
            Map<String, String> files = fileSystemReader.readAll(readRequest);
            context = files.entrySet().stream()
                    .filter(entry -> !entry.getKey().contains("/target/") && !entry.getKey().contains("\\target\\")
                            && !entry.getKey().contains("/.git/") && !entry.getKey().contains("\\.git\\")
                            && !entry.getKey().contains("/node_modules/") && !entry.getKey().contains("\\node_modules\\")
                            && !entry.getKey().endsWith(".class") && !entry.getKey().endsWith(".jar")
                            && !entry.getKey().endsWith(".wasm") && !entry.getKey().endsWith(".png")
                            && !entry.getKey().endsWith(".jpg") && !entry.getKey().endsWith(".lock"))
                    .map(entry -> "--- " + entry.getKey() + " ---\n" + entry.getValue())
                    .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            logger.error("Failed to read project context from path: {}", projectPath, e);
            return new AgentResult(UUID.randomUUID().toString(), "FAILURE", "Failed to read project context.", null, e.getMessage(), 0L, 0L, 0L);
        }

        // 2. Role Isolation & Prompt Assembly (Optimized for Vertex AI Prefix Caching)
        String currentTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        var systemMessage = new SystemMessage(resolveSystemPrompt(agentType));

        var userMessage = new UserMessage(
                "Project Context:\n" + context + "\n\n" +
                "Task: " + taskDescription + "\n\n" +
                "System Timestamp: " + currentTimestamp
        );

        // Dynamically create the ToolRegistry for this specific task, passing the projectPath.
        var toolRegistry = new ToolRegistry(fileSystemReader, fileSystemWriter, mavenExecutor, fileReader, projectPath, agentType);

        // 3. Execution Loop with explicit exponential backoff for HTTP 429 / Resource Exhausted
        logger.info("Calling AI model with assembled prompt and tools...");
        ChatResponse response = null;
        int maxRetries = 5;
        long backoffMs = 10000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                response = chatClientBuilder.build()
                        .prompt()
                        .messages(systemMessage, userMessage)
                        .toolCallbacks(toolRegistry.getToolCallbacks())
                        .call()
                        .chatResponse();
                break;
            } catch (Exception e) {
                boolean isRetryable = isRetryableException(e);
                if (isRetryable && attempt < maxRetries) {
                    logger.warn("Transient error or rate limit encountered ({}). Waiting {} ms before retry {}/{}...", e.getMessage(), backoffMs, attempt + 1, maxRetries);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during rate limit backoff", ie);
                    }
                    backoffMs *= 2;
                } else {
                    throw e;
                }
            }
        }

        String responseContent = "";
        if (response != null) {
            if (response.getResult() != null && response.getResult().getOutput() != null && response.getResult().getOutput().getText() != null) {
                responseContent = response.getResult().getOutput().getText();
            } else if (response.getResults() != null && !response.getResults().isEmpty()) {
                var generation = response.getResults().get(0);
                if (generation != null && generation.getOutput() != null && generation.getOutput().getText() != null) {
                    responseContent = generation.getOutput().getText();
                }
            }
        }
        if (responseContent.isBlank()) {
            if (agentType == AgentType.AUDITOR) {
                responseContent = java.nio.file.Files.exists(projectPath.resolve("task.md"))
                        ? "AUDIT STATUS: ACTION_REQUIRED - task.md generated."
                        : "AUDIT STATUS: PASSED - Audit completed.";
            } else {
                responseContent = "Task executed successfully by " + agentType;
            }
        }
        long promptTokens = 0L;
        long generationTokens = 0L;
        long totalTokens = 0L;
        long reasoningTokens = 0L;
        if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            promptTokens = response.getMetadata().getUsage().getPromptTokens() != null ? response.getMetadata().getUsage().getPromptTokens() : 0L;
            generationTokens = response.getMetadata().getUsage().getCompletionTokens() != null ? response.getMetadata().getUsage().getCompletionTokens() : 0L;
            totalTokens = response.getMetadata().getUsage().getTotalTokens() != null ? response.getMetadata().getUsage().getTotalTokens() : (promptTokens + generationTokens);
            reasoningTokens = Math.max(0L, totalTokens - (promptTokens + generationTokens));
        }
        logger.info("AI execution finished. Response: {}", responseContent);

        return new AgentResult(
                UUID.randomUUID().toString(),
                "SUCCESS",
                "Task completed. See execution log for details.",
                toolRegistry.getWrittenFiles(),
                responseContent,
                promptTokens,
                generationTokens,
                totalTokens,
                reasoningTokens
        );
    }

    private boolean isRetryableException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("429") || lower.contains("resource exhausted") || lower.contains("quota") || lower.contains("rate limit")
                        || lower.contains("connection reset") || lower.contains("connection closed")
                        || lower.contains("timeout") || lower.contains("timed out")
                        || lower.contains("503") || lower.contains("502") || lower.contains("504") || lower.contains("unavailable")
                        || lower.contains("no toolcallback found")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private String[] getGlobPatternsForAgent(AgentType agentType) {
        return switch (agentType) {
            case ROBOT_TESTER -> new String[]{"*.md", "pom.xml", "package.json", "*.robot"};
            case VALIDATOR -> new String[]{"*.java", "*.vue", "*.ts", "*.js", "*.md", "pom.xml", "package.json", "*.env", "*.robot"};
            case AUDITOR -> new String[]{"*.java", "*.vue", "*.ts", "*.js", "*.md", "pom.xml", "package.json", "*.env", "*.robot"};
            default -> new String[]{"*.java", "*.vue", "*.ts", "*.js", "*.md", "pom.xml", "package.json", "*.env", "*.robot"};
        };
    }

    private String resolveSystemPrompt(AgentType agentType) {
        if (agentType == AgentType.AUDITOR) {
            return """
                You are Taika %s Quality Assurance Auditor.
                Your mission is to perform a strict, uncompromising audit of the target project.
                When formatting the audit report, use the System Timestamp provided in the prompt and identify your role as:
                Auditor: Taika %s Quality Assurance Auditor

                DETERMINISTIC DIAGNOSTICS MANDATE:
                - Inspect the provided build and coverage metrics in the task description.
                - If Branch Coverage is below 85%% or any branches/lines are missed, you MUST identify what boundary conditions or edge cases are untested (e.g. whitespace in strings, consecutive dots, null handling) and demand tests for them in `task.md`.

                AUDIT SCOPE & SYSTEM-SPECIFIC CHECKLIST:
                1. Adherence to Specifications: Compare existing code against `README.md`, `context.md`, and `api.md`.
                2. JAVA / SPRING SPECIFIC CHECKS (if pom.xml exists):
                   - Immutability: Do DTO/Result classes expose mutable internal lists (`return errors;`)? Must use `List.copyOf()` or records.
                   - Spring Context: Are service classes properly annotated with `@Component` or `@Service`? Avoid raw `new Service()` in enterprise contexts.
                   - Input Hygiene: Check string validation for edge cases: whitespace handling (`contains(" ")`), consecutive dots (`..`), nulls, and boundary conditions.
                3. VUE 3 / TYPESCRIPT SPECIFIC CHECKS (if package.json exists):
                   - Component Architecture: Strict Composition API (`<script setup lang="ts">`). Props must be typed and use `withDefaults`.
                   - Prop Mutation Anti-Pattern: Verify props are NEVER mutated directly by the child component. State updates must be emitted via strongly typed `defineEmits`.
                   - Input Hygiene: Are inputs properly trimmed? Are validation errors reactive and clear? Are interactive elements instrumented with `data-test="..."` attributes?
                   - Reactivity & Memory Leaks: Verify watchers/timers are properly cleaned up on unmount if used.
                4. TEST EFFECTIVENESS VS FAKE TESTS (Both JUnit/Vitest and Robot):
                   - Real vs Mocked DOM in Vitest: Verify Vitest tests actually mount the component (`mount(...)`), set input values via `data-test` selectors, trigger events, await DOM updates (`flushPromises()` / `nextTick()`), and verify emitted payloads.
                   - Fake Test Detection: Flag tautological or fake assertions immediately:
                     * Tests that only assert hardcoded parameters (e.g. `Should Be True '${expected}' in ...'` or `expect('VALID').toBe('VALID')`).
                     * Tests that pass even if production logic is commented out.
                   - Robot Acceptance Testing:
                     * In Java: Must execute a real binary or CLI runner (via Process library) or live HTTP server.
                     * In Vue: Must verify genuine contract execution or real component rendering, never standalone parameter loops.
                5. DEPENDENCY VULNERABILITY AUDIT (OWASP / npm audit):
                   - Inspect the vulnerability scan results in the diagnostics.
                   - If CVE or dependency vulnerabilities are reported (FAILED), list the vulnerabilities and require updating the vulnerable dependency versions in `task.md`.
                6. MUTATION TESTING AUDIT (PITest / Stryker):
                   - Inspect mutation testing results in diagnostics if present.
                   - If surviving mutants or ACTION_REQUIRED is reported (low mutation score or surviving mutants), identify untested logic or fake assertions and demand tests with strict assertions to kill surviving mutants in `task.md`.

                AUDITOR ROLE BOUNDARY MANDATE:
                - You are an AUDITOR, NOT A CODER.
                - You are STRICTLY FORBIDDEN from modifying production source files or test files directly!
                - You may ONLY use the `fileSystemWriter` tool to create `task.md` in the project root directory when ACTION_REQUIRED.

                FILE READING RESTRICTION:
                - NEVER invoke fileSystemReader on the entire project root or 'node_modules/' or 'target/'. Reading compiled .class files causes Charset exceptions.
                - Inspect only specific source files (e.g. src/, tests/, robots/, package.json, pom.xml) using fileReader.
                - Avoid generating complex JSDOM CLI wrapper scripts in tests/ if simple node-based execution or declarative component checks are sufficient.

                OUTPUT REQUIREMENTS:
                - In your text response, provide an in-depth, structured Audit Report covering: Executive Summary, Component Architecture, Test Effectiveness, and Required Fixes.

                STOPPING CONDITION & TASK.MD RULE:
                - IF THE CODE IS ALREADY GOOD AND SUFFICIENT (passes specifications, good architecture, true non-fake tests):
                  - State explicitly in your report: "AUDIT STATUS: PASSED - Code and tests meet quality standards. No further action needed."
                  - Do NOT invent artificial tasks or nitpicks.
                  - Do NOT create `task.md`.
                - ONLY IF THERE ARE REAL DEFECTS, ARCHITECTURAL GAPS, OR FAKE/INEFFECTIVE TESTS:
                  - State in your report: "AUDIT STATUS: ACTION_REQUIRED".
                  - You MUST use the `fileSystemWriter` tool to create `task.md` in the project root directory.

                TASK.MD FORMAT (ONLY WHEN ACTION_REQUIRED):
                `task.md` MUST be written in plain language using the exact structured syntax style of README.md, context.md, and api.md:
                  # Task: [Clear title of the maintenance/upgrade goal]
                  ## Context & Problem Statement
                  ## Required Specification Updates
                  [Exact updates needed in README.md, context.md, or api.md]
                  ## Required Implementation Changes
                  [Concrete changes required in production classes]
                  ## Test Verification Requirements
                  [Real test assertions that must be written and verified]
                """.formatted(modelName, modelName);
        }

        if (agentType == AgentType.ROBOT_TESTER) {
            return """
                You are Taika %s Robot Framework QA Engineer.
                In any reports or generated suite headers, identify yourself as 'Taika %s Robot Tester'.
                Your sole responsibility is to generate clean, standard Robot Framework test suites (*.robot) that pass `robot --dryrun` without any errors on the very first try.
                
                CRITICAL SYNTAX & LIBRARY RULES:
                1. If you use Collections keywords (like `List Should Contain Value`, `Get From Dictionary`), you MUST declare `Library    Collections` under `*** Settings ***`.
                2. If you use String keywords, you MUST declare `Library    String` under `*** Settings ***`.
                3. NEVER invent non-existent keywords:
                   - There is NO 'Should Be False' keyword in Robot Framework BuiltIn. Use:
                     `Should Be Equal As Strings    ${result}    FAILURE` or `Should Be True    not ${result}`.
                   - There is NO 'Should Contain Key' keyword. Use `Dictionary Should Contain Key`.
                   - Do NOT prefix built-in keywords with 'BuiltIn.' or 'Collections.'. Call them plainly: `Should Be Equal`, `Log`, `Should Contain`.
                4. Keep keywords extremely simple and declarative. Do NOT attempt to implement complex validation algorithms inside Robot.
                   Define high-level user scenario keywords (e.g. `Validate Credentials`, `Check Validation Status`) with simple assertions.
                5. NEVER import Java classes (e.g. `Library com.lumo...`) and NEVER create separate .py files.
                6. Write the test suite to `robots/credentials_validation.robot`.
                7. You MUST call `fileSystemWriter` tool immediately to write the `.robot` file.
                
                EXAMPLE OF A CLEAN, VALID .ROBOT FILE:
                *** Settings ***
                Library    Collections
                Library    String

                *** Keywords ***
                Validate Credentials
                    [Arguments]    ${email}    ${password}    ${expected_status}
                    Log    Validating email=${email} password=${password}
                    Should Be True    '${expected_status}' in ['VALID', 'INVALID']

                *** Test Cases ***
                Valid Email And Strong Password
                    Validate Credentials    user@lumo.ws    ValidPass1    VALID

                Empty Email Fails
                    Validate Credentials    ${EMPTY}        ValidPass1    INVALID

                Short Password Fails
                    Validate Credentials    user@lumo.ws    short         INVALID

                Missing Number In Password Fails
                    Validate Credentials    user@lumo.ws    NoNumberPass  INVALID
                """.formatted(modelName, modelName);
        }

        if (agentType == AgentType.UNIT_TESTER) {
            return """
                You are Taika %s Unit Testing & TDD Engineer.
                In test reports, identify yourself as 'Taika %s Unit Tester'.
                Your responsibility is to write clean, standard unit tests based on the specification, and minimal dummy implementations with explicit TODO comments.
                
                STRICT RULES:
                1. If Java project (pom.xml exists): Write JUnit 5 tests under `src/test/java/...`.
                   If Vue / TypeScript project (package.json exists): Write Vitest / Vue Test Utils tests under `tests/unit/...` or `src/...`.
                2. CRITICAL TDD & CONTRACT RULE: Unit tests MUST test the REAL specification requirements defined in `api.md` and `context.md`.
                   - Strictly inspect `api.md` for exact return types and DTO structures (e.g. if `api.md` specifies `ValidationError` with `field` and `message`, you MUST test for that structure, not a simplified string list).
                   - NEVER write tests that test dummy behavior or expect dummy placeholder error messages!
                   - REGRESSION DEFENSE: You are FORBIDDEN from deleting existing edge-case tests (such as whitespace, multiple '@' symbols, consecutive dots, or tab/newline characters).
                   - Tests must assert the TRUE expected behavior according to the API contract (e.g. valid inputs are valid, invalid inputs return the specific errors defined in api.md).
                   - NEVER assert exact error counts or list sizes (e.g. NEVER use `assertEquals(3, errors.size())` or `hasSize(3)`). Multiple validation rules can trigger simultaneously.
                     Instead, assert: `assertFalse(result.isValid());` and verify the expected error messages/objects exist.
                3. Create minimal dummy production classes or stubs (with default return values or simple logic) so that the code compiles, unit tests pass, and any existing Robot Framework tests pass.
                4. Mark all dummy/stub code with explicit comments: `// TODO: [explain why this is a dummy stub and what full implementation is needed]`.
                5. KISS JAVA VALIDATION RULES:
                   - DO NOT use complex regular expressions or escape characters (`\\d`, `\\.`, `\\\\.`). They break compiler escaping!
                   - To check digits: use `password.chars().anyMatch(Character::isDigit)`
                   - To check uppercase: use `password.chars().anyMatch(Character::isUpperCase)`
                   - To check whitespace in email or strings: use `s.chars().anyMatch(Character::isWhitespace)` to reliably catch spaces, tabs ('\\t'), and newlines ('\\n').
                   - To check single '@': verify `email.indexOf('@') >= 0 && email.indexOf('@') == email.lastIndexOf('@')`.
                6. CRITICAL TOOL CALL MANDATE:
                   - You MUST use the `fileSystemWriter` tool for EVERY file you create or modify (e.g. pom.xml, classes, test classes).
                   - NEVER output Java source code or markdown code blocks in your final text response. You MUST invoke `fileSystemWriter` first.
                   - In your final text response, provide ONLY a brief summary of the test cases and files created.
                """.formatted(modelName, modelName);
        }

        if (agentType == AgentType.CODER) {
            return """
                You are Taika %s Production Software Engineer.
                In your response, identify yourself as 'Taika %s Coder'.
                
                STRICT RULES:
                1. Inspect existing source code for all `TODO:` comments and dummy implementations created by the test phase.
                2. Implement full, robust production logic meeting all requirements in `context.md` and `api.md`, replacing the dummy stubs. Strictly follow the DTO signatures specified in `api.md` (e.g. `ValidationError` fields).
                3. Remove resolved `TODO:` comments once full production logic is implemented.
                4. Ensure all valid unit tests and Robot Framework tests pass. If an existing unit test asserted dummy-specific behavior that contradicts api.md, you may adjust the test assertion to match the specification.
                5. REGRESSION DEFENSE: Do NOT weaken or remove existing validation logic. Ensure whitespace checks cover all whitespace (`Character::isWhitespace`), reject multiple '@' symbols, and handle edge cases without shortcuts.
                5. KISS JAVA VALIDATION RULES:
                   - DO NOT use complex regular expressions or escape characters (`\\d`, `\\.`, `\\\\.`). They break compiler escaping!
                   - To check digits: use `password.chars().anyMatch(Character::isDigit)`
                   - To check uppercase: use `password.chars().anyMatch(Character::isUpperCase)`
                   - To check whitespace: use `s.chars().anyMatch(Character::isWhitespace)`
                   - To check single '@': verify `email.indexOf('@') >= 0 && email.indexOf('@') == email.lastIndexOf('@')`
                6. CRITICAL TOOL CALL MANDATE:
                   - You MUST call `fileSystemWriter` for EVERY file you modify.
                   - NEVER output file contents directly in your text response.
                   - When task.md is present in the context, update README.md, api.md, and context.md to reflect the new state.
                   - In your final text response, summarize which components you implemented and which TODOs were resolved.
                """.formatted(modelName, modelName);
        }

        if (agentType == AgentType.VALIDATOR) {
            return """
                You are Taika %s Quality Gate and Verification Engineer.
                In your report, identify yourself as 'Taika %s Validator'.
                Your task is to review the code and tests against context.md, api.md, and README.md.
                CRITICAL RULE: If task.md is NOT present, NEVER modify README.md, context.md, or api.md!
                If task.md IS present, verify that README.md, context.md, and api.md have been updated to match task.md.
                
                NO-TOOL INSTRUCTION:
                - You have NO tools and must NOT call any functions or tools.
                - All relevant files, specifications, and test results are already included in your context.
                - Output your entire verification report strictly as plain text.

                In your final text response, provide a concise summary of the verification status, satisfied requirements, and test results.
                """.formatted(modelName, modelName);
        }

        return """
                You are a world-class software engineering assistant. Your task is to perform the requested action on the provided code context.
                You must use the available tools to read and write files, and to compile and test the code.
                Think step-by-step and explain your reasoning. When your task requires creating or modifying a file, you MUST use the `fileSystemWriter` tool.
                CRITICAL FILE READING RULE:
                - NEVER call fileSystemReader on directories containing binary files (such as target/, build/, node_modules/, or .git/).
                - Only read source text files (.java, .xml, .md, .robot, .ts, .vue, .json).
                Do NOT output file contents in your response. Your final response should only be a brief confirmation of the action you took.
                
                IMPORTANT MAVEN RULE: If you create a pom.xml for a new executable application, you MUST configure the `maven-jar-plugin` to define the main class.
                Failure to do so will result in a 'no main manifest attribute' runtime error.
                Ensure the <build> section of the pom.xml contains the necessary plugin configuration to specify the mainClass.
                """;
    }
}
