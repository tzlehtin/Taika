package dev.taika.agent.service;

import dev.taika.api.AgentResult;
import dev.taika.api.AgentType;
import dev.taika.tools.service.FileSystemReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private FileSystemReader fileSystemReader;

    @InjectMocks
    private AgentService agentService;

    private void setupChatClientMocks() {
        // Mock the fluent API of ChatClient
        var mockChatClient = mock(ChatClient.class);
        var mockRequestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        var mockCallSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.build()).thenReturn(mockChatClient);
        when(mockChatClient.prompt()).thenReturn(mockRequestSpec); // Chaining part 1
        when(mockRequestSpec.messages(any(), any())).thenReturn(mockRequestSpec); // Chaining part 2
        when(mockRequestSpec.tools(any(String.class), any(String.class), any(String.class), any(String.class))).thenReturn(mockRequestSpec); // Chaining part 3 - specific match
        when(mockRequestSpec.call()).thenReturn(mockCallSpec); // Chaining part 4
        when(mockCallSpec.content()).thenReturn("AI response content");
    }

    @Test
    void executeTask_shouldSucceedWhenContextIsReadable() throws IOException {
        // Arrange
        setupChatClientMocks(); // Setup mocks only for this test
        when(fileSystemReader.readAll(any(FileSystemReader.ReadRequest.class)))
                .thenReturn(Map.of("file.java", "public class Test {}"));

        // Act
        AgentResult result = agentService.executeTask(Path.of("/fake/path"), AgentType.CODER, "Implement feature");

        // Assert
        assertNotNull(result);
        assertEquals("SUCCESS", result.status());
        assertEquals("AI response content", result.executionLog());
    }

    @Test
    void executeTask_shouldFailWhenContextReadFails() throws IOException {
        // Arrange
        String errorMessage = "Disk read error";
        when(fileSystemReader.readAll(any(FileSystemReader.ReadRequest.class)))
                .thenThrow(new IOException(errorMessage));

        // Act
        AgentResult result = agentService.executeTask(Path.of("/fake/path"), AgentType.CODER, "Implement feature");

        // Assert
        assertNotNull(result);
        assertEquals("FAILURE", result.status());
        assertEquals("Failed to read project context.", result.summary());
        assertNotNull(result.executionLog());
        assertTrue(result.executionLog().contains(errorMessage));
    }
}