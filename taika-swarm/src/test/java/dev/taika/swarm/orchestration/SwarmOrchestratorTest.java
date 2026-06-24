package dev.taika.swarm.orchestration;

import dev.taika.agent.service.AgentService;
import dev.taika.api.AgentResult;
import dev.taika.api.AgentType;
import dev.taika.swarm.orchestration.model.SwarmResult;
import dev.taika.swarm.orchestration.model.SwarmStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SwarmOrchestratorTest {

    @Mock
    private AgentService agentService;

    @InjectMocks
    private SwarmOrchestrator swarmOrchestrator;

    private final Path fakePath = Path.of("/fake/project");
    private final String mainObjective = "Implement the main feature.";

    @Test
    void orchestrate_shouldSucceedAfterFullCycle() {
        // Arrange
        var successResult = new AgentResult(UUID.randomUUID().toString(), "SUCCESS", "Success", List.of(), "");

        // Mock the agent service to return SUCCESS for all agent types
        when(agentService.executeTask(any(Path.class), any(AgentType.class), anyString())).thenReturn(successResult);

        // Act
        SwarmResult result = swarmOrchestrator.orchestrate(fakePath, mainObjective);

        // Assert
        assertEquals(SwarmStatus.SUCCESS, result.status());
        assertEquals(3, result.invocationHistory().size());
        assertEquals(AgentType.CODER, result.invocationHistory().get(0).type());
        assertEquals(AgentType.TESTER, result.invocationHistory().get(1).type());
        assertEquals(AgentType.VALIDATOR, result.invocationHistory().get(2).type());
    }

    @Test
    void orchestrate_shouldLoopBackToCoderOnTesterFailure() {
        // Arrange
        var successResult = new AgentResult(UUID.randomUUID().toString(), "SUCCESS", "Success", List.of(), "");
        var failureResult = new AgentResult(UUID.randomUUID().toString(), "FAILURE", "Tests failed", List.of(), "Error log...");

        // Coder succeeds, Tester fails, Coder succeeds again, then the rest succeed.
        when(agentService.executeTask(eq(fakePath), eq(AgentType.CODER), any(String.class)))
                .thenReturn(successResult) // First call
                .thenReturn(successResult); // Second call (after failure)
        when(agentService.executeTask(eq(fakePath), eq(AgentType.TESTER), any(String.class)))
                .thenReturn(failureResult)  // First call to TESTER fails
                .thenReturn(successResult); // Second call to TESTER succeeds
        when(agentService.executeTask(eq(fakePath), eq(AgentType.VALIDATOR), any(String.class))).thenReturn(successResult);

        // Act
        SwarmResult result = swarmOrchestrator.orchestrate(fakePath, mainObjective);

        // Assert
        assertEquals(SwarmStatus.SUCCESS, result.status());
        assertEquals(5, result.invocationHistory().size());
        assertEquals(AgentType.CODER, result.invocationHistory().get(0).type());
        assertEquals(AgentType.TESTER, result.invocationHistory().get(1).type());
        assertEquals("FAILURE", result.invocationHistory().get(1).status());
        assertEquals(AgentType.CODER, result.invocationHistory().get(2).type()); // Loop back
    }

    @Test
    void orchestrate_shouldFailAfterMaxIterations() {
        // Arrange
        var failureResult = new AgentResult(UUID.randomUUID().toString(), "FAILURE", "Always fails", List.of(), "");
        when(agentService.executeTask(any(Path.class), any(AgentType.class), anyString())).thenReturn(failureResult);

        // Act
        SwarmResult result = swarmOrchestrator.orchestrate(fakePath, mainObjective);

        // Assert
        assertEquals(SwarmStatus.MAX_ITERATIONS_REACHED, result.status());
        assertFalse(result.invocationHistory().isEmpty());
    }

    @Test
    void orchestrate_shouldFailOnUnhandledAgentType() {
        // Arrange
        // Create a spy of the real orchestrator to override its state transition method
        SwarmOrchestrator spiedOrchestrator = org.mockito.Mockito.spy(new SwarmOrchestrator(agentService));

        // Mock the agent service to always succeed
        when(agentService.executeTask(any(Path.class), any(AgentType.class), anyString()))
                .thenReturn(new AgentResult(UUID.randomUUID().toString(), "SUCCESS", "Success", List.of(), ""));

        // Force the state transition logic to throw an exception when it receives an unhandled type
        org.mockito.Mockito.doThrow(new IllegalStateException("Unhandled agent type"))
                .when(spiedOrchestrator).getNextState(AgentType.CODER);

        // Act
        // We expect the loop to throw an exception, which we catch and verify.
        assertThrows(IllegalStateException.class, () -> spiedOrchestrator.orchestrate(fakePath, mainObjective));
    }

    @Test
    void getNextState_shouldThrowExceptionForUnhandledAgentType() {
        // Arrange (no mocks needed for this pure method)

        // Act & Assert
        // Directly test the protected method with an agent type not handled by the switch case.
        assertThrows(IllegalStateException.class, () -> swarmOrchestrator.getNextState(AgentType.INTERFACE_DESIGNER));
    }
}