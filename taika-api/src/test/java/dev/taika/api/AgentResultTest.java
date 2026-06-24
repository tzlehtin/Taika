package dev.taika.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentResultTest {

    @Test
    void shouldCreateAgentResultWithCorrectValues() {
        // Arrange
        String taskId = "task-123";
        String status = "SUCCESS";
        String summary = "Implemented feature X.";
        List<String> modifiedFiles = List.of("/path/to/File.java");
        String executionLog = "mvn clean install ... SUCCESS";

        // Act
        AgentResult result = new AgentResult(taskId, status, summary, modifiedFiles, executionLog);

        // Assert
        assertEquals(taskId, result.taskId());
        assertEquals(status, result.status());
        assertEquals(summary, result.summary());
        assertEquals(modifiedFiles, result.modifiedFiles());
        assertEquals(executionLog, result.executionLog());
    }
}