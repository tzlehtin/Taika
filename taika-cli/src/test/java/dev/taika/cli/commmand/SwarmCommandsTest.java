package dev.taika.cli.command;

import dev.taika.swarm.orchestration.SwarmOrchestrator;
import dev.taika.swarm.orchestration.model.SwarmResult;
import dev.taika.swarm.orchestration.model.SwarmStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
}