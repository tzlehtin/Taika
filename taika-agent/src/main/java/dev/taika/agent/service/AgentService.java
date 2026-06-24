package dev.taika.agent.service;

import dev.taika.api.AgentResult;
import dev.taika.api.AgentType;
import dev.taika.tools.service.FileSystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
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

    public AgentService(ChatClient.Builder chatClientBuilder, FileSystemReader fileSystemReader) {
        this.chatClientBuilder = chatClientBuilder;
        this.fileSystemReader = fileSystemReader;
    }

    public AgentResult executeTask(Path projectPath, AgentType agentType, String taskDescription) {
        logger.info("Executing task '{}' for project '{}' with agent type {}", taskDescription, projectPath, agentType);

        // 1. Context Assembly: Load all relevant project files.
        String context;
        // Correctly handle the Map returned by readAll
        try {
            var readRequest = new FileSystemReader.ReadRequest(projectPath.toAbsolutePath().toString(), new String[]{"*.java", "*.md", "pom.xml"});
            Map<String, String> files = fileSystemReader.readAll(readRequest);
            context = files.entrySet().stream()
                    .map(entry -> "--- " + entry.getKey() + " ---\n" + entry.getValue())
                    .collect(Collectors.joining("\n\n"));
        } catch (Exception e) { // Catching a broader exception class for simplicity
            logger.error("Failed to read project context from path: {}", projectPath, e);
            return new AgentResult(UUID.randomUUID().toString(), "FAILURE", "Failed to read project context.", null, e.getMessage());
        }

        // 2. Role Isolation & Prompt Assembly
        // For now, using a generic system prompt. In the future, this could load from a file based on AgentType.
        var systemPromptTemplate = new SystemPromptTemplate("""
                You are a world-class software engineering assistant. Your task is to perform the requested action on the provided code context.
                You must use the available tools to read and write files, and to compile and test the code.
                Think step-by-step and explain your reasoning.
                """);
        var systemMessage = systemPromptTemplate.createMessage();

        var userMessage = new org.springframework.ai.chat.messages.UserMessage(
                "Task: " + taskDescription + "\n\n" +
                "Project Context:\n" + context
        );

        // 3. Execution Loop (simplified to a single call for this implementation)
        logger.info("Calling AI model with assembled prompt and tools...");
        String responseContent = chatClientBuilder.build()
                .prompt(new Prompt(java.util.List.of(systemMessage, userMessage)))
                // Spring AI will automatically discover the Function beans from ToolConfiguration
                .call()
                .content();

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
}