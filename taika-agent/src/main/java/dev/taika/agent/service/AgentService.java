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
                java.util.List.of(), // Placeholder
                responseContent
        );
    }

    private String[] getGlobPatternsForAgent(AgentType agentType) {
        return switch (agentType) {
            case ROBOT_TESTER -> new String[]{"*.md", "pom.xml", "package.json", "*.robot"};
            case VALIDATOR -> new String[]{"*.md", "pom.xml", "package.json"};
            default -> new String[]{"*.java", "*.vue", "*.ts", "*.js", "*.md", "pom.xml", "package.json", "*.env", "*.robot"};
        };
    }

    private String resolveSystemPrompt(AgentType agentType) {
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
                6. You MUST call `fileSystemWriter` tool immediately to write the `.robot` file.
                
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