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
    private static final int DEFAULT_MAX_CODE_LOOPS = 8;

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
     * Executes the deterministic code generation process (TDD unit tests + dummy stubs -> implementation -> verification).
     */
    public SwarmResult generateCode(Path projectPath) {
        return generateCode(projectPath, DEFAULT_MAX_CODE_LOOPS, null);
    }

    public SwarmResult generateCode(Path projectPath, int maxLoops) {
        return generateCode(projectPath, maxLoops, null);
    }

    public SwarmResult generateCode(Path projectPath, int maxLoops, String objective) {
        logger.info("Executing generateCode loop for project '{}' with max loops: {}", projectPath, maxLoops);
        var thread = new ExecutionThread();

        String customGoal = (objective != null && !objective.isBlank()) ? " Objective: " + objective : "";

        // PHASE 1: UNIT_TESTER (Steps 1, 2, 3, 4)
        Path existingRobotFile = findRobotFile(projectPath);
        String robotInstruction = (existingRobotFile != null)
                ? "\nNOTE: Robot Framework acceptance tests exist at '" + projectPath.relativize(existingRobotFile) + "'. Your dummy implementations and tests must ensure BOTH unit tests and Robot tests pass!"
                : "";

        String unitTestTask = """
                Read context.md and api.md.%s%s
                1. Generate comprehensive unit tests (JUnit 5 for Java under src/test/java, or Vitest for Vue under tests/unit or src/).
                CRITICAL: In tests, assert `assertFalse(result.isValid())` and `assertTrue(errors.contains("..."))`. NEVER assert exact error list size (do NOT use `assertEquals(3, errors.size())`).
                CRITICAL: Unit tests must assert the TRUE specification requirements. Do NOT write tests that expect dummy behavior or placeholder errors.
                2. Create initial dummy/stub code or minimal implementations so that BOTH unit tests and any existing Robot Framework tests can compile and pass. Add clear 'TODO: [description]' comments explaining why this quick dummy fix needs proper implementation and what logic remains to be completed.
                3. In Java, DO NOT use regular expressions. Use Character.isDigit, Character.isUpperCase, and simple string checks (contains('@'), etc.).
                4. You MUST call fileSystemWriter separately for EVERY file created (pom.xml, classes, tests). NEVER output code in text responses.
                5. In your response, report what tests were created and what edge cases they cover.
                """.formatted(customGoal, robotInstruction);

        String testReport = "";
        boolean phase1Passed = false;

        for (int loop = 1; loop <= maxLoops; loop++) {
            logger.info("Unit test generation iteration {}/{}", loop, maxLoops);
            try {
                AgentResult result = agentService.executeTask(projectPath, AgentType.UNIT_TESTER, unitTestTask);
                thread.log(new AgentInvocationLog(AgentType.UNIT_TESTER, result.status()));

                if (!"SUCCESS".equalsIgnoreCase(result.status())) {
                    unitTestTask = "The previous unit test generation attempt failed: " + result.summary() + "\nPlease generate valid tests and dummy stubs.";
                    continue;
                }

                testReport = result.executionLog();

                TestRunResult testRunResult = executeUnitTestRun(projectPath);
                if (testRunResult.passed()) {
                    logger.info("Unit tests and dummy stubs passed test execution on iteration {}", loop);
                    phase1Passed = true;
                    break;
                }

                String noFilesWarning = result.modifiedFiles().isEmpty()
                        ? "\n\nCRITICAL ERROR: You did NOT call 'fileSystemWriter' on the previous step, so ZERO files were written to disk! Outputting code in markdown text does nothing. You MUST call 'fileSystemWriter' now!"
                        : "";

                logger.warn("Unit test execution failed on iteration {}. Output:\n{}", loop, testRunResult.output());
                unitTestTask = String.format(
                        "Unit tests or dummy stubs failed compilation or test execution.\n" +
                        "Compiler/Test output:\n%s\n\n" +
                        "CRITICAL FIX INSTRUCTIONS:%s\n" +
                        "1. You MUST call `fileSystemWriter` for EVERY file you modify (both test files and implementation files). Do NOT output code in markdown text.\n" +
                        "2. In Java, DO NOT use regular expressions. Use Character.isDigit, Character.isUpperCase, and simple string checks.\n" +
                        "3. Do NOT assert exact error collection sizes like assertEquals(3, errors.size()). Assert `assertFalse(result.isValid())` and `assertTrue(errors.contains(...))`.\n" +
                        "Please fix the files using fileSystemWriter so that all tests compile and pass.",
                        testRunResult.output(), noFilesWarning
                );
            } catch (Exception e) {
                logger.error("Error executing UNIT_TESTER for project '{}'", projectPath, e);
                writeHtmlReport(projectPath, "taika-report-gen-code.html", "Taika Code Generation Report", SwarmStatus.MAX_ITERATIONS_REACHED, "Execution failed in unit test phase: " + e.getMessage(), thread);
                return new SwarmResult(thread.getId(), SwarmStatus.MAX_ITERATIONS_REACHED, thread.getHistory(), "Execution failed in unit test phase: " + e.getMessage());
            }
        }

        if (!phase1Passed) {
            String summary = String.format("Unit test phase did not pass verification within %d iterations.", maxLoops);
            writeHtmlReport(projectPath, "taika-report-gen-code.html", "Taika Code Generation Report", SwarmStatus.MAX_ITERATIONS_REACHED, summary + "\n\n" + testReport, thread);
            return new SwarmResult(thread.getId(), SwarmStatus.MAX_ITERATIONS_REACHED, thread.getHistory(),
                    summary);
        }

        // PHASE 2: CODER (Steps 5, 6)
        String coderTask = """
                Unit tests (and any Robot tests) are passing with initial dummy code.%s%s
                1. Inspect all files for 'TODO' comments and dummy stubs.
                2. Replace all dummy implementations with full production-ready code matching context.md and api.md.
                3. Remove resolved 'TODO' comments.
                4. Ensure all unit tests AND any existing Robot Framework tests continue to pass. If an existing unit test asserted dummy-specific behavior, adjust the assertion to match the specification.
                5. When writing regex in Java, ensure valid backslash escaping (\\\\d, \\\\w).
                5. Use fileSystemWriter to write the updated production files.
                """.formatted(customGoal, robotInstruction);

        boolean phase2Passed = false;
        String coderSummary = "";

        for (int loop = 1; loop <= maxLoops; loop++) {
            logger.info("Production code implementation iteration {}/{}", loop, maxLoops);
            try {
                AgentResult result = agentService.executeTask(projectPath, AgentType.CODER, coderTask);
                thread.log(new AgentInvocationLog(AgentType.CODER, result.status()));

                if (!"SUCCESS".equalsIgnoreCase(result.status())) {
                    coderTask = "The previous implementation attempt failed: " + result.summary() + "\nPlease complete the implementation.";
                    continue;
                }

                coderSummary = result.executionLog();

                TestRunResult testRunResult = executeUnitTestRun(projectPath);
                if (testRunResult.passed()) {
                    logger.info("Production code passed all test executions on iteration {}", loop);
                    phase2Passed = true;
                    break;
                }

                logger.warn("Production code test execution failed on iteration {}. Output:\n{}", loop, testRunResult.output());
                coderTask = String.format(
                        "Production code failed compilation or tests.\n" +
                        "Compiler/Test output:\n%s\n\n" +
                        "Please fix the production code using fileSystemWriter so all tests compile and pass.",
                        testRunResult.output()
                );
            } catch (Exception e) {
                logger.error("Error executing CODER for project '{}'", projectPath, e);
                writeHtmlReport(projectPath, "taika-report-gen-code.html", "Taika Code Generation Report", SwarmStatus.MAX_ITERATIONS_REACHED, "Execution failed in coder phase: " + e.getMessage(), thread);
                return new SwarmResult(thread.getId(), SwarmStatus.MAX_ITERATIONS_REACHED, thread.getHistory(), "Execution failed in coder phase: " + e.getMessage());
            }
        }

        if (!phase2Passed) {
            String summary = String.format("Production code phase did not pass test verification within %d iterations.", maxLoops);
            writeHtmlReport(projectPath, "taika-report-gen-code.html", "Taika Code Generation Report", SwarmStatus.MAX_ITERATIONS_REACHED, summary + "\n\n" + coderSummary, thread);
            return new SwarmResult(thread.getId(), SwarmStatus.MAX_ITERATIONS_REACHED, thread.getHistory(),
                    summary);
        }

        // PHASE 3: VALIDATOR (Step 7)
        logger.info("Invoking VALIDATOR for final quality verification...");
        String validatorTask = """
                All unit tests pass and production code has been implemented.%s
                Verify that the implementation satisfies all requirements in context.md, api.md, and README.md.
                CRITICAL: Do NOT modify or overwrite README.md, context.md, or api.md! Those are immutable specification files.
                Summarize:
                1. Requirement verification status against specification.
                2. Unit test suite and Robot test suite verification.
                3. Production components implemented.
                4. Verification status (all tests passing).
                """.formatted(customGoal);

        try {
            AgentResult valResult = agentService.executeTask(projectPath, AgentType.VALIDATOR, validatorTask);
            thread.log(new AgentInvocationLog(AgentType.VALIDATOR, valResult.status()));

            String finalReport = String.format(
                    "Code Generation Complete!\n\n" +
                    "=== Test Suite Report ===\n%s\n\n" +
                    "=== Implementation Summary ===\n%s\n\n" +
                    "=== Quality & Verification Report ===\n%s",
                    testReport, coderSummary, valResult.executionLog()
            );

            writeHtmlReport(projectPath, "taika-report-gen-code.html", "Taika Code Generation Report", SwarmStatus.SUCCESS, finalReport, thread);

            return new SwarmResult(thread.getId(), SwarmStatus.SUCCESS, thread.getHistory(), finalReport);
        } catch (Exception e) {
            logger.error("Error executing VALIDATOR for project '{}'", projectPath, e);
            writeHtmlReport(projectPath, "taika-report-gen-code.html", "Taika Code Generation Report", SwarmStatus.MAX_ITERATIONS_REACHED, "Execution failed in validator phase: " + e.getMessage(), thread);
            return new SwarmResult(thread.getId(), SwarmStatus.MAX_ITERATIONS_REACHED, thread.getHistory(), "Execution failed in validator phase: " + e.getMessage());
        }
    }

    record TestRunResult(boolean passed, String output) {}

    protected TestRunResult executeUnitTestRun(Path projectPath) {
        TestRunResult buildResult = runBuildToolTests(projectPath);
        if (!buildResult.passed()) {
            return buildResult;
        }

        Path robotTestFile = findRobotFile(projectPath);
        if (robotTestFile != null) {
            logger.info("Robot test file found at '{}'. Running Robot test validation...", robotTestFile);
            TestRunResult robotResult = executeRobotRun(projectPath, robotTestFile);
            if (!robotResult.passed()) {
                return new TestRunResult(false, "Unit tests passed, but Robot Framework tests failed:\n\n" + robotResult.output());
            }
            return new TestRunResult(true, buildResult.output() + "\n\n" + robotResult.output());
        }

        return buildResult;
    }

    protected TestRunResult runBuildToolTests(Path projectPath) {
        try {
            ProcessBuilder pb;
            if (Files.exists(projectPath.resolve("pom.xml"))) {
                logger.info("Executing Maven test validation on: {}", projectPath);
                pb = new ProcessBuilder("mvn", "test", "-B");
            } else if (Files.exists(projectPath.resolve("package.json"))) {
                logger.info("Executing npm test validation on: {}", projectPath);
                pb = new ProcessBuilder("npm", "test");
            } else {
                return new TestRunResult(false, "No pom.xml or package.json found in project directory.");
            }

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
            return new TestRunResult(exitCode == 0, output.toString());
        } catch (Exception e) {
            logger.error("Failed to execute unit test process for project '{}'", projectPath, e);
            return new TestRunResult(false, "System error running test validation: " + e.getMessage());
        }
    }

    protected TestRunResult executeRobotRun(Path projectPath, Path robotTestFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder("robot", robotTestFile.toString());
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
            return new TestRunResult(exitCode == 0, output.toString());
        } catch (java.io.IOException e) {
            logger.error("Failed to execute 'robot' process. Is Robot Framework installed and on PATH?", e);
            return new TestRunResult(false, "System error running 'robot': " + e.getMessage() + ". Please verify that Robot Framework is installed and accessible via PATH.");
        } catch (Exception e) {
            return new TestRunResult(false, "Failed to run 'robot': " + e.getMessage());
        }
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
                    String reportSummary = String.format("Robot tests generated and verified with dry-run in %d iteration(s):\n%s", loop, result.executionLog());
                    writeHtmlReport(projectPath, "taika-report-gen-robots.html", "Taika Robot Generation Report", SwarmStatus.SUCCESS, reportSummary, thread);
                    return new SwarmResult(thread.getId(), SwarmStatus.SUCCESS, thread.getHistory(),
                            reportSummary);
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
                writeHtmlReport(projectPath, "taika-report-gen-robots.html", "Taika Robot Generation Report", SwarmStatus.MAX_ITERATIONS_REACHED, "Execution failed: " + e.getMessage(), thread);
                return new SwarmResult(thread.getId(), SwarmStatus.MAX_ITERATIONS_REACHED, thread.getHistory(), "Execution failed: " + e.getMessage());
            }
        }

        String failSummary = String.format("Robot test generation and dry-run validation did not succeed within %d iterations.", maxLoops);
        writeHtmlReport(projectPath, "taika-report-gen-robots.html", "Taika Robot Generation Report", SwarmStatus.MAX_ITERATIONS_REACHED, failSummary, thread);
        return new SwarmResult(thread.getId(), SwarmStatus.MAX_ITERATIONS_REACHED, thread.getHistory(),
                failSummary);
    }

    private void writeHtmlReport(Path projectPath, String fileName, String title, SwarmStatus status, String content, ExecutionThread thread) {
        try {
            Path reportFile = projectPath.resolve(fileName);
            String statusColor = (status == SwarmStatus.SUCCESS) ? "#10b981" : "#ef4444";
            String sanitizedContent = content
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\n", "<br/>");

            StringBuilder historyHtml = new StringBuilder();
            for (AgentInvocationLog log : thread.getHistory()) {
                historyHtml.append(String.format("<li><strong>%s</strong>: %s</li>", log.type(), log.status()));
            }

            String html = String.format("""
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>%s</title>
                        <style>
                            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background: #f8fafc; color: #1e293b; margin: 0; padding: 2rem; }
                            .container { max-width: 900px; margin: 0 auto; background: #ffffff; border-radius: 8px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1); padding: 2rem; }
                            h1 { margin-top: 0; font-size: 1.75rem; color: #0f172a; }
                            .badge { display: inline-block; padding: 0.35rem 0.75rem; border-radius: 9999px; font-weight: 600; color: #ffffff; background: %s; margin-bottom: 1.5rem; }
                            .history { background: #f1f5f9; padding: 1rem 1.5rem; border-radius: 6px; margin-bottom: 1.5rem; }
                            .history ul { margin: 0.5rem 0 0 0; padding-left: 1.2rem; }
                            .content { background: #0f172a; color: #e2e8f0; padding: 1.5rem; border-radius: 6px; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; font-size: 0.9rem; line-height: 1.6; overflow-x: auto; }
                            footer { margin-top: 2rem; font-size: 0.8rem; color: #64748b; text-align: center; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <h1>%s</h1>
                            <div class="badge">%s</div>
                            <div class="history">
                                <strong>Agent Invocation Steps (%d):</strong>
                                <ul>%s</ul>
                            </div>
                            <div class="content">%s</div>
                            <footer>Generated by Taika Swarm Orchestrator</footer>
                        </div>
                    </body>
                    </html>
                    """, title, statusColor, title, status, thread.getHistory().size(), historyHtml, sanitizedContent);

            Files.writeString(reportFile, html);
            logger.info("Human-readable HTML report written to: {}", reportFile);
        } catch (Exception e) {
            logger.warn("Could not write HTML report to '{}': {}", projectPath, e.getMessage());
        }
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

    protected Path findRobotFile(Path projectPath) {
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
                return new StateTransition(AgentType.VALIDATOR, "The project has been implemented and tested. Verify that the implementation satisfies context.md, api.md, and README.md. Do NOT overwrite or modify README.md, context.md, or api.md. Summarize the verification status.");
            case VALIDATOR:
                return new StateTransition(null, "Task complete."); // null indicates completion
            default:
                throw new IllegalStateException("Unhandled agent type in state transition: " + currentAgent);
        }
    }
}