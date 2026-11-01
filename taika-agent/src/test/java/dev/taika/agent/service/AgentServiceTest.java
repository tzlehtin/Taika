package dev.taika.agent.service;

import dev.taika.agent.tools.ToolRegistry;
import dev.taika.api.AgentResult;
import dev.taika.api.AgentType;
import dev.taika.tools.service.FileSystemReader;
import dev.taika.tools.service.FileSystemWriter;
import dev.taika.tools.service.MavenExecutor;
import dev.taika.tools.service.FileReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatModel chatModel;

    @Mock
    private FileSystemReader fileSystemReader;

    @Mock
    private FileSystemWriter fileSystemWriter;

    @Mock
    private MavenExecutor mavenExecutor;

    @Mock
    private FileReader fileReader;

    @InjectMocks
    private AgentService agentService;

    private final Path fakePath = Path.of("/fake/path");
    private final String taskDescription = "Implement feature";

    @BeforeEach
    void setUp() {
        // No general setup needed, mocks will be configured per-test.
    }

    @Test
    void executeTask_shouldSucceedWhenContextIsReadable() throws IOException {
        // Arrange
        when(chatClientBuilder.build()).thenReturn(chatClient); // Configure mock only where it's used
        
        // Mock the fluent API chain correctly
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        // This is the correct way to mock a fluent API chain.
        // Each method call must be stubbed to return the next object in the chain.
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(any(), any())).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(any(List.class))).thenReturn(requestSpec); // .toolCallbacks() also returns a request spec for chaining
        when(requestSpec.call()).thenReturn(callResponseSpec);

        // Correctly mock the .chatResponse() call and its nested objects to match the implementation
        ChatResponse mockChatResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        when(callResponseSpec.chatResponse()).thenReturn(mockChatResponse);
        when(mockChatResponse.getResult()).thenReturn(mockGeneration);
        when(mockGeneration.getOutput()).thenReturn(new AssistantMessage("AI response"));

        Map<String, String> fakeFiles = Map.of("file1.java", "public class MyClass {}");
        when(fileSystemReader.readAll(any(FileSystemReader.ReadRequest.class))).thenReturn(fakeFiles);

        // Act
        AgentResult result = agentService.executeTask(fakePath, AgentType.CODER, taskDescription);

        // Assert
        assertEquals("SUCCESS", result.status());
        assertEquals("AI response", result.executionLog());
    }

    @Test
    void executeTask_shouldFailWhenContextReadFails() throws IOException {
        // Arrange
        when(fileSystemReader.readAll(any(FileSystemReader.ReadRequest.class))).thenThrow(new IOException("Disk read error"));

        // Act
        AgentResult result = agentService.executeTask(fakePath, AgentType.CODER, taskDescription);

        // Assert
        assertEquals("FAILURE", result.status());
        assertEquals("Failed to read project context.", result.summary());
        assertNotNull(result.executionLog());
    }

    @Test
    void executeTask_shouldSucceedWithRobotTesterRole() throws IOException {
        // Arrange
        when(chatClientBuilder.build()).thenReturn(chatClient);

        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(any(), any())).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(any(List.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        ChatResponse mockChatResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        when(callResponseSpec.chatResponse()).thenReturn(mockChatResponse);
        when(mockChatResponse.getResult()).thenReturn(mockGeneration);
        when(mockGeneration.getOutput()).thenReturn(new AssistantMessage("Robot test suite generated."));

        Map<String, String> fakeFiles = Map.of(
                "context.md", "Context content",
                "api.md", "API specification"
        );
        when(fileSystemReader.readAll(any(FileSystemReader.ReadRequest.class))).thenReturn(fakeFiles);

        // Act
        AgentResult result = agentService.executeTask(fakePath, AgentType.ROBOT_TESTER, "Generate robot tests");

        // Assert
        assertEquals("SUCCESS", result.status());
        assertEquals("Robot test suite generated.", result.executionLog());
    }
}