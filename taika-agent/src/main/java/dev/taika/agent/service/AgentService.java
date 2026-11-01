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
import org.springframework.ai.chat.model.Generation;
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

    public AgentService(ChatClient.Builder chatClientBuilder,
                        FileSystemReader fileSystemReader,
                        FileSystemWriter fileSystemWriter,
                        MavenExecutor mavenExecutor,
                        FileReader fileReader) {
        this.chatClientBuilder = chatClientBuilder;
        this.fileSystemReader = fileSystemReader;
        this.fileSystemWriter = fileSystemWriter;
        this.mavenExecutor = mavenExecutor;
        this.fileReader = fileReader;
    }

    public AgentResult executeTask(Path projectPath, AgentType agentType, String taskDescription) {
        logger.info("Executing task '{}' for project '{}' with agent type {}", taskDescription, projectPath, agentType);

        // 1. Context Assembly: Load all relevant project files.
        String context;
        // Correctly handle the Map returned by readAll
        try {
            String[] globPatterns = getGlobPatternsForAgent(agentType);

            logger.info("Assembling context for agent {} with glob patterns: {}", agentType, String.join(", ", globPatterns));
            var readRequest = new FileSystemReader.ReadRequest(projectPath.toAbsolutePath().toString(), globPatterns);
            Map<String, String> files = fileSystemReader.readAll(readRequest);
            context = files.entrySet().stream()
                    .filter(entry -> !entry.getKey().contains("/target/") && !entry.getKey().contains("\\target\\")
                            && !entry.getKey().contains("/.git/") && !entry.getKey().contains("\\.git\\")
                            && !entry.getKey().contains("/node_modules/") && !entry.getKey().contains("\\node_modules\\")
                            && !entry.getKey().endsWith(".class") && !entry.getKey().endsWith(".jar"))
                    .map(entry -> "--- " + entry.getKey() + " ---\n" + entry.getValue())
                    .collect(Collectors.joining("\n\n"));
        } catch (Exception e) { // Catching a broader exception class for simplicity
            logger.error("Failed to read project context from path: {}", projectPath, e);
            return new AgentResult(UUID.randomUUID().toString(), "FAILURE", "Failed to read project context.", null, e.getMessage());
        }

        // 2. Role Isolation & Prompt Assembly
        var systemMessage = new SystemMessage(resolveSystemPrompt(agentType));

        var userMessage = new UserMessage(
                "Task: " + taskDescription + "\n\n" +
                "Project Context:\n" + context
        );

        // Dynamically create the ToolRegistry for this specific task, passing the projectPath.
        var toolRegistry = new ToolRegistry(fileSystemReader, fileSystemWriter, mavenExecutor, fileReader, projectPath);

        // 3. Execution Loop (simplified to a single call for this implementation)
        logger.info("Calling AI model with assembled prompt and tools...");
        ChatResponse response = chatClientBuilder.build()
                .prompt()
                .messages(systemMessage, userMessage)
                .toolCallbacks(toolRegistry.getToolCallbacks())
                .call()
                .chatResponse(); // Get the final response object as shown in the working example.

        // Use the correct .getText() method as proven by the provided example.
        String responseContent = response.getResult().getOutput().getText();
        logger.info("AI execution finished. Response: {}", responseContent);


        // For now, we return the direct AI response. A real implementation would parse this
        // and extract structured data like modified files.
        return new AgentResult(
                UUID.randomUUID().toString(),
                "SUCCESS",
                "Task completed. See execution log for details.",
                toolRegistry.getWrittenFiles(),
                responseContent
        );
    }

    private String[] getGlobPatternsForAgent(AgentType agentType) {
        return switch (agentType) {
            case ROBOT_TESTER -> new String[]{"*.md", "pom.xml", "package.json", "*.robot"};
            case VALIDATOR -> new String[]{"*.md", "pom.xml", "package.json"};
            case AUDITOR -> new String[]{"*.java", "*.vue", "*.ts", "*.js", "*.md", "pom.xml", "package.json", "*.env", "*.robot"};
            default -> new String[]{"*.java", "*.vue", "*.ts", "*.js", "*.md", "pom.xml", "package.json", "*.env", "*.robot"};
        };
    }

    private String resolveSystemPrompt(AgentType agentType) {
        if (agentType == AgentType.AUDITOR) {
            return """
                You are a Principal Software Architect and Quality Assurance Auditor.
                Your mission is to perform a strict, uncompromising audit of the target project.

                AUDIT SCOPE:
                1. Adherence to Specifications: Compare existing code against `README.md`, `context.md`, and `api.md`.
                2. Architecture & Design: Identify missing annotations (e.g. Spring `@Service`), lack of structured error models (e.g. field-specific error DTOs instead of raw strings), thread safety, and maintainability.
                3. Test Effectiveness vs Fake Tests: Inspect test suites (both JUnit/Vitest and Robot Framework).
                   - Flag tautological or fake assertions (e.g. Robot tests that only assert their own parameters without calling production logic or APIs).
                   - Verify edge case coverage, boundary conditions, and true functional verification.
                4. Code Quality & Security: Inspect input validation robustness (e.g. email/password rules) and potential edge-case leaks.

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
                  [Clear explanation of the gaps identified during audit]
                  ## Required Specification Updates
                  [Exact updates needed in README.md, context.md, or api.md]
                  ## Required Implementation Changes
                  [Concrete changes required in production classes]
                  ## Test Verification Requirements
                  [Real test assertions that must be written and verified]
                """;
        }

        if (agentType == AgentType.ROBOT_TESTER) {
            return """
                You are a specialized Robot Framework QA Engineer.
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
                """;
        }

        if (agentType == AgentType.UNIT_TESTER) {
            return """
                You are a specialized Unit Testing & Test-Driven Development (TDD) Engineer.
                Your responsibility is to write clean, standard unit tests based on the specification, and minimal dummy implementations with explicit TODO comments.
                
                STRICT RULES:
                1. If Java project (pom.xml exists): Write JUnit 5 tests under `src/test/java/...`.
                   If Vue / TypeScript project (package.json exists): Write Vitest / Vue Test Utils tests under `tests/unit/...` or `src/...`.
                2. CRITICAL TDD RULE: Unit tests MUST test the REAL specification requirements defined in `api.md` and `context.md`.
                   - NEVER write tests that test dummy behavior or expect dummy placeholder error messages!
                   - Tests must assert the TRUE expected behavior according to the API contract (e.g. valid inputs are valid, invalid inputs return the specific errors defined in api.md).
                   - NEVER assert exact error counts or list sizes (e.g. NEVER use `assertEquals(3, errors.size())` or `hasSize(3)`). Multiple validation rules can trigger simultaneously.
                     Instead, always assert: `assertFalse(result.isValid());` and `assertTrue(result.getErrors().contains("Expected error"));`
                3. Create minimal dummy production classes or stubs (with default return values or simple logic) so that the code compiles, unit tests pass, and any existing Robot Framework tests pass.
                4. Mark all dummy/stub code with explicit comments: `// TODO: [explain why this is a dummy stub and what full implementation is needed]`.
                5. KISS JAVA VALIDATION RULES:
                   - DO NOT use complex regular expressions or escape characters (`\\d`, `\\.`, `\\\\.`). They break compiler escaping!
                   - To check digits: use `password.chars().anyMatch(Character::isDigit)`
                   - To check uppercase: use `password.chars().anyMatch(Character::isUpperCase)`
                   - To check email: use simple string checks (e.g. `email.contains("@") && email.contains(".") && !email.startsWith("@") && !email.endsWith(".")`)
                6. CRITICAL TOOL CALL MANDATE:
                   - You MUST use the `fileSystemWriter` tool for EVERY file you create or modify (e.g. pom.xml, classes, test classes).
                   - NEVER output Java source code or markdown code blocks in your final text response. You MUST invoke `fileSystemWriter` first.
                   - In your final text response, provide ONLY a brief summary of the test cases and files created.
                """;
        }

        if (agentType == AgentType.CODER) {
            return """
                You are a world-class software engineer responsible for implementing production code.
                
                STRICT RULES:
                1. Inspect existing source code for all `TODO:` comments and dummy implementations created by the test phase.
                2. Implement full, robust production logic meeting all requirements in `context.md` and `api.md`, replacing the dummy stubs.
                3. Remove resolved `TODO:` comments once full production logic is implemented.
                4. Ensure all valid unit tests and Robot Framework tests pass. If an existing unit test asserted dummy-specific behavior that contradicts api.md, you may adjust the test assertion to match the specification.
                5. KISS JAVA VALIDATION RULES:
                   - DO NOT use complex regular expressions or escape characters (`\\d`, `\\.`, `\\\\.`). They break compiler escaping!
                   - To check digits: use `password.chars().anyMatch(Character::isDigit)`
                   - To check uppercase: use `password.chars().anyMatch(Character::isUpperCase)`
                   - To check email: use simple string checks (e.g. `email.contains("@") && email.contains(".") && !email.startsWith("@") && !email.endsWith(".")`)
                6. CRITICAL TOOL CALL MANDATE:
                   - You MUST call `fileSystemWriter` for EVERY file you modify.
                   - NEVER output file contents directly in your text response.
                   - When task.md is present in the context, update README.md, api.md, and context.md to reflect the new state.
                   - In your final text response, summarize which components you implemented and which TODOs were resolved.
                """;
        }

        if (agentType == AgentType.VALIDATOR) {
            return """
                You are a Quality Gate and Verification Engineer.
                Your task is to review the code and tests against context.md, api.md, and README.md.
                CRITICAL RULE: If task.md is NOT present, NEVER modify README.md, context.md, or api.md!
                If task.md IS present, verify that README.md, context.md, and api.md have been updated to match task.md.
                In your final text response, provide a concise summary of the verification status, satisfied requirements, and test results.
                """;
        }

        return """
                You are a world-class software engineering assistant. Your task is to perform the requested action on the provided code context.
                You must use the available tools to read and write files, and to compile and test the code.
                Think step-by-step and explain your reasoning. When your task requires creating or modifying a file, you MUST use the `fileSystemWriter` tool.
                Do NOT output file contents in your response. Your final response should only be a brief confirmation of the action you took.
                
                IMPORTANT MAVEN RULE: If you create a pom.xml for a new executable application, you MUST configure the `maven-jar-plugin` to define the main class.
                Failure to do so will result in a 'no main manifest attribute' runtime error.
                Ensure the <build> section of the pom.xml contains the necessary plugin configuration to specify the mainClass.
                """;
    }
}