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
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void generateRobotTests_shouldSucceedWhenAgentReturnsSuccess() {
        // Arrange
        SwarmOrchestrator spiedOrchestrator = org.mockito.Mockito.spy(swarmOrchestrator);
        var successResult = new AgentResult(UUID.randomUUID().toString(), "SUCCESS", "Robot tests created", List.of(), "Suite details...");
        when(agentService.executeTask(eq(fakePath), eq(AgentType.ROBOT_TESTER), anyString())).thenReturn(successResult);
        org.mockito.Mockito.doReturn(new SwarmOrchestrator.DryRunResult(true, "Dry run passed"))
                .when(spiedOrchestrator).executeRobotDryRun(fakePath);

        // Act
        SwarmResult result = spiedOrchestrator.generateRobotTests(fakePath);

        // Assert
        assertEquals(SwarmStatus.SUCCESS, result.status());
    }

    @Test
    void generateRobotTests_shouldRetryAndSucceedWhenDryRunFailsInitially() {
        // Arrange
        SwarmOrchestrator spiedOrchestrator = org.mockito.Mockito.spy(swarmOrchestrator);
        var successResult = new AgentResult(UUID.randomUUID().toString(), "SUCCESS", "Generated tests", List.of(), "Log output");
        when(agentService.executeTask(eq(fakePath), eq(AgentType.ROBOT_TESTER), anyString())).thenReturn(successResult);

        // 1. iteration fails dry-run, 2. iteration passes
        org.mockito.Mockito.doReturn(new SwarmOrchestrator.DryRunResult(false, "Syntax error in .robot file"))
                .doReturn(new SwarmOrchestrator.DryRunResult(true, "Dry run passed"))
                .when(spiedOrchestrator).executeRobotDryRun(fakePath);

        // Act
        SwarmResult result = spiedOrchestrator.generateRobotTests(fakePath, 3);

        // Assert
        assertEquals(SwarmStatus.SUCCESS, result.status());
        assertEquals(2, result.invocationHistory().size());
        assertTrue(result.finalSummary().contains("in 2 iteration(s)"));
    }

    @Test
    void generateRobotTests_shouldFailWhenMaxLoopsReached() {
        // Arrange
        SwarmOrchestrator spiedOrchestrator = org.mockito.Mockito.spy(swarmOrchestrator);
        var successResult = new AgentResult(UUID.randomUUID().toString(), "SUCCESS", "Generated tests", List.of(), "Log output");
        when(agentService.executeTask(eq(fakePath), eq(AgentType.ROBOT_TESTER), anyString())).thenReturn(successResult);
        org.mockito.Mockito.doReturn(new SwarmOrchestrator.DryRunResult(false, "Persistent keyword error"))
                .when(spiedOrchestrator).executeRobotDryRun(fakePath);

        // Act
        SwarmResult result = spiedOrchestrator.generateRobotTests(fakePath, 3);

        // Assert
        assertEquals(SwarmStatus.MAX_ITERATIONS_REACHED, result.status());
        assertEquals(3, result.invocationHistory().size());
        assertTrue(result.finalSummary().contains("did not succeed within 3 iterations"));
    }

    @Test
    void generateRobotTests_shouldHandleAgentExecutionException() {
        // Arrange
        when(agentService.executeTask(eq(fakePath), eq(AgentType.ROBOT_TESTER), anyString()))
                .thenThrow(new RuntimeException("Model connectivity timeout"));

        // Act
        SwarmResult result = swarmOrchestrator.generateRobotTests(fakePath, 3);

        // Assert
        assertEquals(SwarmStatus.MAX_ITERATIONS_REACHED, result.status());
        assertTrue(result.finalSummary().contains("Execution failed: Model connectivity timeout"));
    }

    @Test
    void executeRobotDryRun_shouldReturnFailureWhenNoRobotFilesFound(@TempDir Path tempDir) {
        // Act
        SwarmOrchestrator.DryRunResult result = swarmOrchestrator.executeRobotDryRun(tempDir);

        // Assert
        assertFalse(result.passed());
        assertTrue(result.output().contains("No .robot files were found"));
    }

    @Test
    void generateCode_shouldSucceedWhenAllPhasesPass() {
        // Arrange
        SwarmOrchestrator spiedOrchestrator = org.mockito.Mockito.spy(swarmOrchestrator);
        var successResult = new AgentResult(UUID.randomUUID().toString(), "SUCCESS", "Step passed", List.of(), "Execution log");
        when(agentService.executeTask(eq(fakePath), any(AgentType.class), anyString())).thenReturn(successResult);
        org.mockito.Mockito.doReturn(new SwarmOrchestrator.TestRunResult(true, "All tests passed"))
                .when(spiedOrchestrator).executeUnitTestRun(fakePath);

        // Act
        SwarmResult result = spiedOrchestrator.generateCode(fakePath, 3);

        // Assert
        assertEquals(SwarmStatus.SUCCESS, result.status());
        assertTrue(result.finalSummary().contains("Code Generation Complete!"));
        assertEquals(3, result.invocationHistory().size()); // UNIT_TESTER, CODER, VALIDATOR
    }

    @Test
    void generateCode_shouldRetryAndSucceedWhenUnitTestFailsInitially() {
        // Arrange
        SwarmOrchestrator spiedOrchestrator = org.mockito.Mockito.spy(swarmOrchestrator);
        var successResult = new AgentResult(UUID.randomUUID().toString(), "SUCCESS", "Step passed", List.of(), "Execution log");
        when(agentService.executeTask(eq(fakePath), any(AgentType.class), anyString())).thenReturn(successResult);

        // 1st unit test run fails, 2nd passes; CODER test run passes
        org.mockito.Mockito.doReturn(new SwarmOrchestrator.TestRunResult(false, "Compilation error in stub"))
                .doReturn(new SwarmOrchestrator.TestRunResult(true, "Tests passed"))
                .doReturn(new SwarmOrchestrator.TestRunResult(true, "Production tests passed"))
                .when(spiedOrchestrator).executeUnitTestRun(fakePath);

        // Act
        SwarmResult result = spiedOrchestrator.generateCode(fakePath, 3);

        // Assert
        assertEquals(SwarmStatus.SUCCESS, result.status());
        assertEquals(4, result.invocationHistory().size()); // UNIT_TESTER x2, CODER x1, VALIDATOR x1
    }

    @Test
    void generateCode_shouldFailWhenUnitTestPhaseExceedsMaxLoops() {
        // Arrange
        SwarmOrchestrator spiedOrchestrator = org.mockito.Mockito.spy(swarmOrchestrator);
        var successResult = new AgentResult(UUID.randomUUID().toString(), "SUCCESS", "Step passed", List.of(), "Execution log");
        when(agentService.executeTask(eq(fakePath), eq(AgentType.UNIT_TESTER), anyString())).thenReturn(successResult);
        org.mockito.Mockito.doReturn(new SwarmOrchestrator.TestRunResult(false, "Persistent test failure"))
                .when(spiedOrchestrator).executeUnitTestRun(fakePath);

        // Act
        SwarmResult result = spiedOrchestrator.generateCode(fakePath, 3);

        // Assert
        assertEquals(SwarmStatus.MAX_ITERATIONS_REACHED, result.status());
        assertTrue(result.finalSummary().contains("Unit test phase did not pass verification"));
    }

    @Test
    void executeUnitTestRun_shouldReturnFailureWhenNoProjectBuildFilesFound(@TempDir Path tempDir) {
        // Act
        SwarmOrchestrator.TestRunResult result = swarmOrchestrator.executeUnitTestRun(tempDir);

        // Assert
        assertFalse(result.passed());
        assertTrue(result.output().contains("No pom.xml or package.json found"));
    }

    @Test
    void executeUnitTestRun_shouldFailWhenRobotTestsFail() {
        // Arrange
        SwarmOrchestrator spiedOrchestrator = org.mockito.Mockito.spy(swarmOrchestrator);
        org.mockito.Mockito.doReturn(new SwarmOrchestrator.TestRunResult(true, "Maven tests passed"))
                .when(spiedOrchestrator).runBuildToolTests(fakePath);
        org.mockito.Mockito.doReturn(fakePath.resolve("src/test/robotframework/credentials_validation.robot"))
                .when(spiedOrchestrator).findRobotFile(fakePath);
        org.mockito.Mockito.doReturn(new SwarmOrchestrator.TestRunResult(false, "1 test failed"))
                .when(spiedOrchestrator).executeRobotRun(eq(fakePath), any(Path.class));

        // Act
        SwarmOrchestrator.TestRunResult result = spiedOrchestrator.executeUnitTestRun(fakePath);

        // Assert
        assertFalse(result.passed());
        assertTrue(result.output().contains("Robot Framework tests failed"));
    }

    @Test
    void executeUnitTestRun_shouldPassWhenBothUnitAndRobotTestsPass() {
        // Arrange
        SwarmOrchestrator spiedOrchestrator = org.mockito.Mockito.spy(swarmOrchestrator);
        org.mockito.Mockito.doReturn(new SwarmOrchestrator.TestRunResult(true, "Maven tests passed"))
                .when(spiedOrchestrator).runBuildToolTests(fakePath);
        org.mockito.Mockito.doReturn(fakePath.resolve("src/test/robotframework/credentials_validation.robot"))
                .when(spiedOrchestrator).findRobotFile(fakePath);
        org.mockito.Mockito.doReturn(new SwarmOrchestrator.TestRunResult(true, "All robot tests passed"))
                .when(spiedOrchestrator).executeRobotRun(eq(fakePath), any(Path.class));

        // Act
        SwarmOrchestrator.TestRunResult result = spiedOrchestrator.executeUnitTestRun(fakePath);

        // Assert
        assertTrue(result.passed());
        assertTrue(result.output().contains("All robot tests passed"));
    }

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
        assertEquals(4, result.invocationHistory().size());
        assertEquals(AgentType.ROBOT_TESTER, result.invocationHistory().get(0).type());
        assertEquals(AgentType.CODER, result.invocationHistory().get(1).type());
        assertEquals(AgentType.TESTER, result.invocationHistory().get(2).type());
        assertEquals(AgentType.VALIDATOR, result.invocationHistory().get(3).type());
    }

    @Test
    void orchestrate_shouldLoopBackToCoderOnTesterFailure() {
        // Arrange
        var successResult = new AgentResult(UUID.randomUUID().toString(), "SUCCESS", "Success", List.of(), "");
        var failureResult = new AgentResult(UUID.randomUUID().toString(), "FAILURE", "Tests failed", List.of(), "Error log...");

        // Robot tester succeeds, Coder succeeds, Tester fails, Coder succeeds again, then the rest succeed.
        when(agentService.executeTask(eq(fakePath), eq(AgentType.ROBOT_TESTER), any(String.class)))
                .thenReturn(successResult);
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
        assertEquals(6, result.invocationHistory().size());
        assertEquals(AgentType.ROBOT_TESTER, result.invocationHistory().get(0).type());
        assertEquals(AgentType.CODER, result.invocationHistory().get(1).type());
        assertEquals(AgentType.TESTER, result.invocationHistory().get(2).type());
        assertEquals("FAILURE", result.invocationHistory().get(2).status());
        assertEquals(AgentType.CODER, result.invocationHistory().get(3).type()); // Loop back
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
                .when(spiedOrchestrator).getNextState(any(AgentType.class));

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