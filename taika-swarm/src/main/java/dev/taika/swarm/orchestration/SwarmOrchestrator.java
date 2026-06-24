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

    public SwarmResult orchestrate(Path projectPath, String mainObjective) {
        logger.info("Starting swarm orchestration for project '{}' with objective: {}", projectPath, mainObjective);

        var thread = new ExecutionThread();
        String currentTask = mainObjective;
        AgentType nextAgent = AgentType.CODER;

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
            switch (nextAgent) {
                case CODER:
                    currentTask = "The code has been written or modified. Write comprehensive JUnit tests to ensure full functionality and code coverage. Run 'mvn test' and ensure it passes.";
                    nextAgent = AgentType.TESTER;
                    break;
                case TESTER:
                    currentTask = "The code is written and all tests pass. Review the code against the project's context.md and api.md. Ensure the implementation matches the documentation. If it does not, provide a detailed list of changes needed. If it does, write a comprehensive README.md for the project.";
                    nextAgent = AgentType.VALIDATOR;
                    break;
                case VALIDATOR:
                    // A "SUCCESS" from the VALIDATOR means the task is complete.
                    // We assume the last step was writing the README.
                    logger.info("VALIDATOR agent succeeded. Swarm orchestration complete.");
                    return new SwarmResult(thread.getId(), SwarmStatus.SUCCESS, thread.getHistory(), "Orchestration completed successfully by VALIDATOR.");
                default:
                    logger.error("Unhandled agent type in orchestration loop: {}", nextAgent);
                    return new SwarmResult(thread.getId(), SwarmStatus.FAILURE, thread.getHistory(), "Internal orchestration error.");
            }
        }

        logger.warn("Orchestration failed after reaching max iterations ({})", MAX_ITERATIONS);
        return new SwarmResult(thread.getId(), SwarmStatus.MAX_ITERATIONS_REACHED, thread.getHistory(), "Orchestration failed to complete within the maximum number of iterations.");
    }
}