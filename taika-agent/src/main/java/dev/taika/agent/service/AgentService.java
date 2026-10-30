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
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
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
        var systemPromptTemplate = new SystemPromptTemplate(resolveSystemPrompt(agentType));
        var systemMessage = systemPromptTemplate.createMessage();

        var userMessage = new org.springframework.ai.chat.messages.UserMessage(
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
                You are a specialized Robot Framework QA Automation Engineer.
                Your sole responsibility is to generate acceptance and process-level test suites in Robot Framework syntax (*.robot).
                
                STRICT RULES:
                1. Analyze `context.md`, `api.md`, and any existing specification files in the project.
                2. Identify key scenarios: happy paths, input validation, and boundary/edge failure cases.
                3. Write the Robot Framework test file into the target project using the `fileSystemWriter` tool (e.g. `src/test/robot/` or `tests/robot/`).
                4. You must ONLY create/modify test files. Do NOT implement or change production application code.
                5. In your final text response, provide a brief summary of the exact test cases and edge cases you created.
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