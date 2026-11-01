package dev.taika.cli.command;

import dev.taika.swarm.orchestration.SwarmOrchestrator;
import dev.taika.swarm.orchestration.model.SwarmResult;
import dev.taika.swarm.orchestration.model.SwarmStatus;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SwarmCommandsTest {

    @InjectMocks
    private SwarmCommands swarmCommands;

    @Mock
    private SwarmOrchestrator swarmOrchestrator;

    @Test
    void execution_shouldReturnSuccessMessage_whenOrchestrationSucceeds(@TempDir Path tempDir) {
        // Arrange
        var successResult = new SwarmResult(UUID.randomUUID().toString(), SwarmStatus.SUCCESS, Collections.emptyList(), "All agents succeeded.");
        when(swarmOrchestrator.orchestrate(any(Path.class), anyString())).thenReturn(successResult);

        // Act
        String output = swarmCommands.execution(tempDir.toString(), "Run tests");

        // Assert
        assertTrue(output.contains("Orchestration Complete!"));
        assertTrue(output.contains("Status: SUCCESS"));
        assertTrue(output.contains("Summary: All agents succeeded."));
    }

    @Test
    void execution_shouldReturnErrorMessage_whenOrchestrationThrowsException(@TempDir Path tempDir) {
        // Arrange
        String errorMessage = "A critical error occurred.";
        when(swarmOrchestrator.orchestrate(any(Path.class), anyString())).thenThrow(new RuntimeException(errorMessage));

        // Act
        String output = swarmCommands.execution(tempDir.toString(), "Run tests");

        // Assert
        assertEquals("An unexpected error occurred during orchestration: " + errorMessage, output);
    }

    @Test
    void execution_shouldReturnValidationError_forNonExistentPath() {
        // Arrange
        String nonExistentPath = "/path/that/does/not/exist";

        // Act & Assert
        assertTrue(swarmCommands.execution(nonExistentPath, "test").contains("Error: The specified path does not exist"));
    }

    @Test
    void audit_shouldSucceedWhenOrchestratorSucceeds(@TempDir Path tempDir) {
        // Arrange
        var successResult = new SwarmResult(UUID.randomUUID().toString(), SwarmStatus.SUCCESS, Collections.emptyList(), "Clean audit.");
        when(swarmOrchestrator.audit(any(Path.class))).thenReturn(successResult);

        // Act
        String output = swarmCommands.audit(tempDir.toString(), null);

        // Assert
        assertTrue(output.contains("Audit Complete!"));
        assertTrue(output.contains("Status: SUCCESS"));
        assertTrue(output.contains("Report File:"));
        assertTrue(output.contains("Task File:"));
    }

    @Test
    void audit_shouldHandleExceptionGracefully(@TempDir Path tempDir) {
        // Arrange
        when(swarmOrchestrator.audit(any(Path.class))).thenThrow(new RuntimeException("Audit error"));

        // Act
        String output = swarmCommands.audit(tempDir.toString(), null);

        // Assert
        assertTrue(output.contains("An unexpected error occurred during audit: Audit error"));
    }

    @Test
    void audit_shouldReturnErrorForInvalidPath() {
        // Act
        String output = swarmCommands.audit("/nonexistent/directory/path", null);

        // Assert
        assertTrue(output.contains("Error: The specified path does not exist"));
    }

    @Test
    void generateCode_shouldSucceedAndDetectTaskMode(@TempDir Path tempDir) throws Exception {
        // Arrange: Create task.md in tempDir to verify task mode logging
        Files.writeString(tempDir.resolve("task.md"), "Task content");
        var successResult = new SwarmResult(UUID.randomUUID().toString(), SwarmStatus.SUCCESS, Collections.emptyList(), "Code generated.");
        when(swarmOrchestrator.generateCode(eq(tempDir), eq(3), eq("Custom objective"))).thenReturn(successResult);

        // Act
        String output = swarmCommands.generateCode(tempDir.toString(), 3, "Custom objective");

        // Assert
        assertTrue(output.contains("Code Generation Complete!"));
        assertTrue(output.contains("Status: SUCCESS"));
        assertTrue(output.contains("Summary:"));
    }

    @Test
    void generateCode_shouldHandleExceptionGracefully(@TempDir Path tempDir) {
        // Arrange
        when(swarmOrchestrator.generateCode(any(Path.class), eq(5), any())).thenThrow(new RuntimeException("Generation failed"));

        // Act
        String output = swarmCommands.generateCode(tempDir.toString(), 5, null);

        // Assert
        assertTrue(output.contains("An unexpected error occurred during code generation: Generation failed"));
    }

    @Test
    void generateRobotTests_shouldSucceedWhenOrchestratorSucceeds(@TempDir Path tempDir) {
        // Arrange
        var successResult = new SwarmResult(UUID.randomUUID().toString(), SwarmStatus.SUCCESS, Collections.emptyList(), "Robot tests ready.");
        when(swarmOrchestrator.generateRobotTests(eq(tempDir), eq(4))).thenReturn(successResult);

        // Act
        String output = swarmCommands.generateRobotTests(tempDir.toString(), 4);

        // Assert
        assertTrue(output.contains("Robot Test Generation Complete!"));
        assertTrue(output.contains("Status: SUCCESS"));
        assertTrue(output.contains("Summary:"));
    }

    @Test
    void pipeline_shouldSucceedWhenOrchestratorSucceeds(@TempDir Path tempDir) {
        // Arrange
        var successResult = new SwarmResult(UUID.randomUUID().toString(), SwarmStatus.SUCCESS, Collections.emptyList(), "Pipeline complete.");
        when(swarmOrchestrator.pipeline(eq(tempDir), eq(3), eq("Custom objective"))).thenReturn(successResult);

        // Act
        String output = swarmCommands.pipeline(tempDir.toString(), 3, "Custom objective");

        // Assert
        assertTrue(output.contains("Pipeline Complete!"));
        assertTrue(output.contains("Status: SUCCESS"));
        assertTrue(output.contains("Summary:"));
    }

    @Test
    void pipeline_shouldHandleExceptionGracefully(@TempDir Path tempDir) {
        // Arrange
        when(swarmOrchestrator.pipeline(any(Path.class), eq(5), any())).thenThrow(new RuntimeException("Pipeline crash"));

        // Act
        String output = swarmCommands.pipeline(tempDir.toString(), 5, null);

        // Assert
        assertTrue(output.contains("An unexpected error occurred during pipeline execution: Pipeline crash"));
    }

    @Test
    void pipeline_shouldReturnErrorForInvalidPath() {
        // Act
        String output = swarmCommands.pipeline("/nonexistent/directory/path", 3, null);

        // Assert
        assertTrue(output.contains("Error: The specified path does not exist"));
    }
}