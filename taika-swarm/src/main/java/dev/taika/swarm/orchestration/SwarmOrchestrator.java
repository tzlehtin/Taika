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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.stream.Stream;
import java.util.Collections;
import java.nio.file.Path;

@Service
public class SwarmOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(SwarmOrchestrator.class);
    private static final int MAX_ITERATIONS = 10; // To prevent infinite loops
    private static final int DEFAULT_MAX_ROBOT_LOOPS = 8;

    private final AgentService agentService;

    public SwarmOrchestrator(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * Executes only the Robot Framework test generation step.
     */
    public SwarmResult generateRobotTests(Path projectPath) {
        return generateRobotTests(projectPath, DEFAULT_MAX_ROBOT_LOOPS);
    }

    /**
     * Executes the Robot Framework test generation step with iterative dry-run verification.
     */
    public SwarmResult generateRobotTests(Path projectPath, int maxLoops) {
        logger.info("Executing ROBOT_TESTER loop for project '{}' with max loops: {}", projectPath, maxLoops);
        var thread = new ExecutionThread();
        String taskDescription = """
                Read context.md and api.md. Generate a simple, clean, and declarative Robot Framework test file (*.robot) for acceptance test scenarios (valid and invalid cases).
                Write the file to src/test/robotframework/credentials_validation.robot using the fileSystemWriter tool.
                RULES:
                - Keep tests simple and declarative using test cases and keywords.
                - Under *** Settings ***, explicitly import 'Library    Collections' and 'Library    String'.
                - Do NOT invent fake keywords like 'Should Be False' or 'Should Contain Key'. Use standard assertions: 'Should Be Equal', 'Should Be True'.
                - Do NOT import Java classes. Do NOT create .py files.
                """;

        for (int loop = 1; loop <= maxLoops; loop++) {
            logger.info("Robot test generation iteration {}/{}", loop, maxLoops);
            try {
                AgentResult result = agentService.executeTask(projectPath, AgentType.ROBOT_TESTER, taskDescription);
                thread.log(new AgentInvocationLog(AgentType.ROBOT_TESTER, result.status()));

                if (!"SUCCESS".equalsIgnoreCase(result.status())) {
                    taskDescription = "The previous generation attempt failed: " + result.summary() + "\nPlease generate valid Robot Framework test files.";
                    continue;
                }

                // Validate generated robot files with dry-run
                DryRunResult dryRunResult = executeRobotDryRun(projectPath);
                if (dryRunResult.passed()) {
                    logger.info("Robot test suite passed dry-run validation on iteration {}", loop);
                    return new SwarmResult(thread.getId(), SwarmStatus.SUCCESS, thread.getHistory(),
                            String.format("Robot tests generated and verified with dry-run in %d iteration(s):\n%s", loop, result.executionLog()));
                }

                logger.warn("Robot dry-run failed on iteration {}. Output:\n{}", loop, dryRunResult.output());
                taskDescription = String.format(
                        "The generated Robot Framework tests failed syntax or keyword validation (robot --dryrun).\n" +
                        "Dry-run output:\n%s\n\n" +
                        "Completely rewrite the .robot file using 'fileSystemWriter'. Keep keywords minimal and standard. Import 'Library  Collections' and 'Library  String' in *** Settings ***. Avoid complex logic.",
                        dryRunResult.output()
                );
            } catch (Exception e) {
                logger.error("Error executing ROBOT_TESTER for project '{}'", projectPath, e);
                return new SwarmResult(thread.getId(), SwarmStatus.MAX_ITERATIONS_REACHED, thread.getHistory(), "Execution failed: " + e.getMessage());
            }
        }

        return new SwarmResult(thread.getId(), SwarmStatus.MAX_ITERATIONS_REACHED, thread.getHistory(),
                String.format("Robot test generation and dry-run validation did not succeed within %d iterations.", maxLoops));
    }

    record DryRunResult(boolean passed, String output) {}

    protected DryRunResult executeRobotDryRun(Path projectPath) {
        try {
            Path robotTestFile = findRobotFile(projectPath);
            if (robotTestFile == null) {
                return new DryRunResult(false, "No .robot files were found in the project directory.");
            }

            logger.info("Executing dry-run validation on: {}", robotTestFile);

            ProcessBuilder pb = new ProcessBuilder("robot", "--dryrun", robotTestFile.toString());
            pb.directory(projectPath.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            return new DryRunResult(exitCode == 0, output.toString());
        } catch (java.io.IOException e) {
            logger.error("Failed to execute 'robot' process. Is Robot Framework installed and on PATH?", e);
            return new DryRunResult(false, "System error running 'robot --dryrun': " + e.getMessage() + ". Please verify that Robot Framework is installed and accessible via PATH.");
        } catch (Exception e) {
            return new DryRunResult(false, "Failed to run 'robot --dryrun': " + e.getMessage());
        }
    }

    private Path findRobotFile(Path projectPath) {
        try (Stream<Path> stream = Files.walk(projectPath, 10)) {
            return stream
                    .filter(p -> !p.toString().contains("/target/") && !p.toString().contains("/.git/"))
                    .filter(p -> p.toString().endsWith(".robot"))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
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