package dev.taika.swarm.orchestration;

import dev.taika.agent.service.AgentService;
import dev.taika.api.AgentResult;
import dev.taika.api.AgentType;
import dev.taika.swarm.orchestration.model.AgentInvocationLog;
import dev.taika.swarm.orchestration.model.ExecutionThread;
import dev.taika.swarm.orchestration.model.SwarmResult;
import dev.taika.swarm.orchestration.model.SwarmStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class SwarmOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(SwarmOrchestrator.class);
    private static final int MAX_ITERATIONS = 10; // To prevent infinite loops

    private final AgentService agentService;

    public SwarmOrchestrator(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * Executes only the Robot Framework test generation step.
     */
    public SwarmResult generateRobotTests(Path projectPath) {
        logger.info("Executing standalone ROBOT_TESTER for project '{}'", projectPath);
        var thread = new ExecutionThread();
        String taskDescription = "Read context.md and api.md. Generate comprehensive Robot Framework test cases covering happy path and edge/failure cases. Write the .robot file to the appropriate test folder.";

        AgentResult result = agentService.executeTask(projectPath, AgentType.ROBOT_TESTER, taskDescription);
        thread.log(new AgentInvocationLog(AgentType.ROBOT_TESTER, result.status()));

        if ("SUCCESS".equalsIgnoreCase(result.status())) {
            return new SwarmResult(thread.getId(), SwarmStatus.SUCCESS, thread.getHistory(),
                    "Robot tests generated successfully:\n" + result.executionLog());
        } else {
            return new SwarmResult(thread.getId(), SwarmStatus.MAX_ITERATIONS_REACHED, thread.getHistory(),
                    "Failed to generate Robot tests: " + result.summary());
        }
    }

    public SwarmResult orchestrate(Path projectPath, String mainObjective) {
        logger.info("Starting swarm orchestration for project '{}' with objective: {}", projectPath, mainObjective);

        var thread = new ExecutionThread();
        AgentType nextAgent = AgentType.ROBOT_TESTER;
        String currentTask = "Analyze context.md and api.md and generate initial Robot Framework integration test cases before writing code.";

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            logger.info("Iteration {}/{} | Invoking {} agent...", i + 1, MAX_ITERATIONS, nextAgent);

            AgentResult result = agentService.executeTask(projectPath, nextAgent, currentTask);
            thread.log(new AgentInvocationLog(nextAgent, result.status()));

            if (!"SUCCESS".equalsIgnoreCase(result.status())) {
                logger.warn("{} agent failed. Rerouting feedback to CODER. Failure summary: {}", nextAgent, result.summary());
                // If any agent fails, formulate a new task for the CODER to fix the issue.
                currentTask = String.format(
                        "The previous attempt by the %s agent failed. Please fix the following issue:\n\n%s\n\nExecution Log:\n%s",
                        nextAgent, result.summary(), result.executionLog()
                );
                nextAgent = AgentType.CODER; // Loop back to the coder
                continue;
            }

            logger.info("{} agent reported SUCCESS.", nextAgent);

            // State transition logic
            StateTransition transition = getNextState(nextAgent);
            nextAgent = transition.nextAgent;
            currentTask = transition.nextTask;

            if (nextAgent == null) { // Completion condition
                logger.info("VALIDATOR agent succeeded. Swarm orchestration complete.");
                return new SwarmResult(thread.getId(), SwarmStatus.SUCCESS, thread.getHistory(), "Orchestration completed successfully by VALIDATOR.");
            }
        }

        logger.warn("Orchestration failed after reaching max iterations ({})", MAX_ITERATIONS);
        return new SwarmResult(thread.getId(), SwarmStatus.MAX_ITERATIONS_REACHED, thread.getHistory(), "Orchestration failed to complete within the maximum number of iterations.");
    }

    protected static class StateTransition {
        final AgentType nextAgent;
        final String nextTask;
        StateTransition(AgentType nextAgent, String nextTask) { this.nextAgent = nextAgent; this.nextTask = nextTask; }
    }

    protected StateTransition getNextState(AgentType currentAgent) {
        switch (currentAgent) {
            case ROBOT_TESTER:
                return new StateTransition(AgentType.CODER, "Robot tests have been created. Implement the production code matching api.md and context.md.");
            case CODER:
                return new StateTransition(AgentType.TESTER, "The code has been written or modified. Write comprehensive JUnit tests to ensure full functionality and code coverage. Run 'mvn test' and ensure it passes.");
            case TESTER:
                return new StateTransition(AgentType.VALIDATOR, "The project has been implemented and tested. Your final and only task is to use the 'fileSystemWriter' tool to write a comprehensive README.md file to the project root, summarizing the project based on the provided context files. Do not respond with the content of the file, just confirm that the tool was called.");
            case VALIDATOR:
                return new StateTransition(null, "Task complete."); // null indicates completion
            default:
                throw new IllegalStateException("Unhandled agent type in state transition: " + currentAgent);
        }
    }
}