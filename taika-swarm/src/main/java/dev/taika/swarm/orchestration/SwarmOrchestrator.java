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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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

    /**
     * Executes the complete automated development pipeline in sequence:
     * 1. generateRobotTests (Acceptance test generation)
     * 2. generateCode (TDD Unit tests -> Stubs -> Verification -> Production code -> Validation)
     * 3. audit (Deep architecture, code quality, test effectiveness, and task.md generation)
     */
    public SwarmResult pipeline(Path projectPath) {
        return pipeline(projectPath, DEFAULT_MAX_CODE_LOOPS, null);
    }

    public SwarmResult pipeline(Path projectPath, int maxLoops) {
        return pipeline(projectPath, maxLoops, null);
    }

    public SwarmResult pipeline(Path projectPath, int maxLoops, String objective) {
        logger.info("Executing automated pipeline (gen-robots -> gen-code -> audit) for project '{}' with max loops: {}", projectPath, maxLoops);

        // Step 1: gen-robots
        logger.info("=== PIPELINE STEP 1/3: Generating Robot Framework acceptance tests ===");
        SwarmResult robotResult = generateRobotTests(projectPath, maxLoops);
        if (robotResult.status() != SwarmStatus.SUCCESS) {
            logger.warn("Pipeline halted at step 1 (gen-robots). Status: {}", robotResult.status());
            return new SwarmResult(robotResult.threadId(), robotResult.status(), robotResult.invocationHistory(),
                    "Pipeline halted at Step 1 (gen-robots):\n" + robotResult.finalSummary());
        }

        // Step 2: gen-code
        logger.info("=== PIPELINE STEP 2/3: Generating code and unit tests with TDD loop ===");
        SwarmResult codeResult = generateCode(projectPath, maxLoops, objective);
        if (codeResult.status() != SwarmStatus.SUCCESS) {
            logger.warn("Pipeline halted at step 2 (gen-code). Status: {}", codeResult.status());
            return new SwarmResult(codeResult.threadId(), codeResult.status(), codeResult.invocationHistory(),
                    "Pipeline halted at Step 2 (gen-code):\n" + codeResult.finalSummary());
        }

        // Step 3: audit
        logger.info("=== PIPELINE STEP 3/3: Executing quality, architecture, and test effectiveness audit ===");
        SwarmResult auditResult = audit(projectPath);

        String combinedSummary = String.format(
                "Pipeline Execution Complete (gen-robots -> gen-code -> audit)!\n\n" +
                "--- Step 1: Robot Tests ---\nStatus: %s\n\n" +
                "--- Step 2: Code Generation ---\nStatus: %s\n\n" +
                "--- Step 3: Audit Result ---\n%s",
                robotResult.status(), codeResult.status(), auditResult.finalSummary()
        );
        return new SwarmResult(auditResult.threadId(), auditResult.status(), auditResult.invocationHistory(), combinedSummary);
    }

    /**
     * Performs a comprehensive quality, architecture, and test effectiveness audit of the project,
     * generating `taika-report-audit.html` and `task.md`.
     */
    public SwarmResult audit(Path projectPath) {
        logger.info("Starting audit for project '{}'", projectPath);
        var thread = new ExecutionThread();

        // Execute automated build, test, and code coverage diagnostics before invoking AI
        String diagnostics = runAuditDiagnostics(projectPath);
        logger.info("Audit diagnostics collected:\n{}", diagnostics);

        String auditTask = """
                Perform a complete and thorough audit of this project.
                
                === OBJECTIVE DETERMINISTIC DIAGNOSTICS (FROM BUILD SYSTEM) ===
                %s
                ===============================================================
                
                1. Verify architectural integrity against context.md, api.md, and README.md.
                2. Inspect test suites for TRUE effectiveness vs fake assertions (e.g. Robot tests that assert their own parameters rather than verifying real components, or tests with low branch coverage).
                3. Identify all concrete code, design, immutability, and specification defects.
                4. Stopping criteria:
                   - If code and tests meet quality standards (including high branch coverage and no fake tests), conclude with 'AUDIT STATUS: PASSED' and DO NOT create `task.md`.
                   - If concrete fixes are required, conclude with 'AUDIT STATUS: ACTION_REQUIRED' and use `fileSystemWriter` to create `task.md` in the project root.
                """.formatted(diagnostics);

        try {
            AgentResult auditResult = agentService.executeTask(projectPath, AgentType.AUDITOR, auditTask);
            thread.log(new AgentInvocationLog(AgentType.AUDITOR, auditResult.status()));

            Path taskMdFile = projectPath.resolve("task.md");
            boolean auditPassed = auditResult.executionLog() != null && auditResult.executionLog().contains("AUDIT STATUS: PASSED");

            if (auditPassed && Files.exists(taskMdFile)) {
                try {
                    Files.deleteIfExists(taskMdFile);
                    logger.info("Audit passed cleanly. Deleted obsolete task.md from project root.");
                } catch (Exception e) {
                    logger.warn("Could not delete resolved task.md: {}", e.getMessage());
                }
            }

            String taskMdNotice;
            if (Files.exists(taskMdFile)) {
                taskMdNotice = "\nAction Required: `task.md` generated at: " + taskMdFile;
            } else {
                taskMdNotice = "\nAudit Result: CLEAN (No `task.md` required. Code and tests meet quality standards).";
            }

            String summary = "Project Audit Complete!\n" + taskMdNotice + "\n\n=== Diagnostics ===\n" + diagnostics + "\n\n=== Audit Findings ===\n" + auditResult.executionLog();
            writeHtmlReport(projectPath, "taika-report-audit.html", "Taika Project Quality & Test Audit Report", SwarmStatus.SUCCESS, summary, thread);

            return new SwarmResult(thread.getId(), SwarmStatus.SUCCESS, thread.getHistory(), summary);
        } catch (Exception e) {
            logger.error("Error executing AUDITOR for project '{}'", projectPath, e);
            String errorSummary = "Audit failed: " + getRootCauseMessage(e);
            writeHtmlReport(projectPath, "taika-report-audit.html", "Taika Project Quality & Test Audit Report", SwarmStatus.MAX_ITERATIONS_REACHED, errorSummary, thread);
            return new SwarmResult(thread.getId(), SwarmStatus.MAX_ITERATIONS_REACHED, thread.getHistory(), errorSummary);
        }
    }

    protected String runAuditDiagnostics(Path projectPath) {
        StringBuilder sb = new StringBuilder();
        boolean hasPom = Files.exists(projectPath.resolve("pom.xml"));
        boolean hasPackageJson = Files.exists(projectPath.resolve("package.json"));

        if (hasPom) {
            sb.append("Target Platform: Java / Maven\n");
            try {
                logger.info("Executing Maven test with JaCoCo coverage on: {}", projectPath);
                ProcessBuilder pb = new ProcessBuilder(
                        "mvn", "org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent",
                        "test", "org.jacoco:jacoco-maven-plugin:0.8.12:report", "-B"
                );
                pb.directory(projectPath.toFile());
                pb.redirectErrorStream(true);
                Process p = pb.start();
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) output.append(line).append("\n");
                }
                int exitCode = p.waitFor();
                sb.append("Maven Test Status: ").append(exitCode == 0 ? "PASSED" : "FAILED").append("\n");

                Path csvReport = projectPath.resolve("target/site/jacoco/jacoco.csv");
                if (Files.exists(csvReport)) {
                    sb.append(parseJaCoCoCsv(csvReport)).append("\n");
                } else {
                    sb.append("Coverage Report: JaCoCo CSV was not generated.\n");
                }

                // Run Static Analysis (SpotBugs)
                String staticAnalysis = runSpotBugsAnalysis(projectPath);
                sb.append(staticAnalysis).append("\n");
            } catch (Exception e) {
                sb.append("Maven execution error: ").append(e.getMessage()).append("\n");
            }
        } else if (hasPackageJson) {
            sb.append("Target Platform: Vue / TypeScript (npm)\n");
            TestRunResult npmResult = runBuildToolTests(projectPath);
            sb.append("NPM Test Status: ").append(npmResult.passed() ? "PASSED" : "FAILED").append("\n");
            sb.append("NPM Test Output Summary:\n").append(npmResult.output().length() > 500
                    ? npmResult.output().substring(0, 500) + "... [truncated]"
                    : npmResult.output()).append("\n");

                // Run Static Analysis / Typecheck for Vue / TypeScript
                String staticAnalysis = runNpmStaticAnalysis(projectPath);
                sb.append(staticAnalysis).append("\n");
        } else {
            sb.append("No pom.xml or package.json found in project directory.\n");
        }

        Path robotTestFile = findRobotFile(projectPath);
        if (robotTestFile != null) {
            TestRunResult robotResult = executeRobotRun(projectPath, robotTestFile);
            sb.append("Robot Acceptance Tests: ").append(robotResult.passed() ? "PASSED" : "FAILED").append("\n");
        } else {
            sb.append("Robot Acceptance Tests: None found.\n");
        }

        return sb.toString();
    }

    protected String runSpotBugsAnalysis(Path projectPath) {
        StringBuilder sb = new StringBuilder();
        try {
            logger.info("Executing SpotBugs static analysis on: {}", projectPath);
            ProcessBuilder pb = new ProcessBuilder(
                    "mvn", "com.github.spotbugs:spotbugs-maven-plugin:4.8.6.6:check",
                    "-Dspotbugs.failOnError=false", "-B"
            );
            pb.directory(projectPath.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append("\n");
            }
            process.waitFor();

            Path spotbugsXml = projectPath.resolve("target/spotbugsXml.xml");
            if (Files.exists(spotbugsXml)) {
                String xml = Files.readString(spotbugsXml);
                int bugCount = (xml.split("<BugInstance ").length) - 1;
                if (bugCount > 0) {
                    sb.append("Static Analysis (SpotBugs): FAILED (").append(bugCount).append(" bug(s) detected)\n");
                    // Extract bug types and messages for the agent
                    xml.lines()
                            .filter(line -> line.contains("<BugInstance ") || line.contains("<LongMessage>"))
                            .limit(10)
                            .forEach(line -> sb.append("  - ").append(line.trim()).append("\n"));
                } else {
                    sb.append("Static Analysis (SpotBugs): PASSED (0 bugs detected)\n");
                }
            } else {
                sb.append("Static Analysis (SpotBugs): PASSED (No bugs or report generated)\n");
            }
        } catch (Exception e) {
            sb.append("Static Analysis (SpotBugs): Could not execute (").append(e.getMessage()).append(")\n");
        }
        return sb.toString().trim();
    }

    protected String runNpmStaticAnalysis(Path projectPath) {
        StringBuilder sb = new StringBuilder();
        sb.append("Static Analysis (TypeCheck / Lint):\n");

        // Run TypeScript typecheck if tsconfig exists
        if (Files.exists(projectPath.resolve("tsconfig.json"))) {
            logger.info("Executing TypeScript static typecheck on: {}", projectPath);
            TestRunResult tscResult = runProcess(projectPath, "npx", "vue-tsc", "--noEmit");
            if (!tscResult.passed()) {
                // Fallback to standard tsc
                tscResult = runProcess(projectPath, "npx", "tsc", "--noEmit");
            }
            sb.append("- Typecheck Status: ").append(tscResult.passed() ? "PASSED" : "FAILED").append("\n");
            if (!tscResult.passed() && !tscResult.output().isBlank()) {
                String snippet = tscResult.output().length() > 400 ? tscResult.output().substring(0, 400) + "... [truncated]" : tscResult.output();
                sb.append("  Details:\n").append(snippet).append("\n");
            }
        }

        return sb.toString();
    }

    private String parseJaCoCoCsv(Path csvPath) {
        try (Stream<String> lines = Files.lines(csvPath)) {
            long lineMissed = 0, lineCovered = 0;
            long branchMissed = 0, branchCovered = 0;
            List<String> list = lines.skip(1).toList();
            for (String row : list) {
                String[] cols = row.split(",");
                if (cols.length >= 9) {
                    branchMissed += Long.parseLong(cols[5].trim());
                    branchCovered += Long.parseLong(cols[6].trim());
                    lineMissed += Long.parseLong(cols[7].trim());
                    lineCovered += Long.parseLong(cols[8].trim());
                }
            }
            long totalLines = lineMissed + lineCovered;
            long totalBranches = branchMissed + branchCovered;
            double linePct = totalLines > 0 ? (lineCovered * 100.0) / totalLines : 100.0;
            double branchPct = totalBranches > 0 ? (branchCovered * 100.0) / totalBranches : 100.0;
            return String.format("Coverage Metrics:\n- Line Coverage: %.1f%% (Covered: %d, Missed: %d)\n- Branch Coverage: %.1f%% (Covered: %d, Missed: %d)",
                    linePct, lineCovered, lineMissed, branchPct, branchCovered, branchMissed);
        } catch (Exception e) {
            return "Failed to parse JaCoCo CSV: " + e.getMessage();
        }
    }

    public SwarmResult generateCode(Path projectPath, int maxLoops) {
        return generateCode(projectPath, maxLoops, null);
    }

    public SwarmResult generateCode(Path projectPath, int maxLoops, String objective) {
        logger.info("Executing generateCode loop for project '{}' with max loops: {}", projectPath, maxLoops);
        var thread = new ExecutionThread();

        String customGoal = (objective != null && !objective.isBlank()) ? " Objective: " + objective : "";
        boolean taskMdPresent = Files.exists(projectPath.resolve("task.md"));
        String taskMdInstruction = taskMdPresent
                ? "\nTASK.MD ACTIVE: 'task.md' is present! You MUST implement the changes, architecture improvements, and test requirements defined in 'task.md'. In this mode, you ARE PERMITTED AND REQUIRED to update README.md, context.md, and api.md to stay in sync with the new implementation."
                : "\nSPECIFICATION INTEGRITY: 'task.md' is not present. README.md, context.md, and api.md are immutable specifications and must NOT be modified.";

        // PHASE 1: UNIT_TESTER (Steps 1, 2, 3, 4)
        Path existingRobotFile = findRobotFile(projectPath);
        String robotInstruction = (existingRobotFile != null)
                ? "\nNOTE: Robot Framework acceptance tests exist at '" + projectPath.relativize(existingRobotFile) + "'. Your dummy implementations and tests must ensure BOTH unit tests and Robot tests pass!"
                : "";

        String unitTestTask = """
                Read context.md, api.md, and README.md.%s%s%s
                1. Generate comprehensive unit tests (JUnit 5 for Java under src/test/java, or Vitest for Vue under tests/unit or src/).
                CRITICAL: In tests, assert `assertFalse(result.isValid())` and `assertTrue(errors.contains("..."))`. NEVER assert exact error list size (do NOT use `assertEquals(3, errors.size())`).
                CRITICAL: Unit tests must assert the TRUE specification requirements. Do NOT write tests that expect dummy behavior or placeholder errors.
                2. Create initial dummy/stub code or minimal implementations so that BOTH unit tests and any existing Robot Framework tests can compile and pass. Add clear 'TODO: [description]' comments explaining why this quick dummy fix needs proper implementation and what logic remains to be completed.
                3. In Java, DO NOT use regular expressions. Use Character.isDigit, Character.isUpperCase, and simple string checks (contains('@'), etc.).
                4. You MUST call fileSystemWriter separately for EVERY file created (pom.xml, classes, tests). NEVER output code in text responses.
                5. In your response, report what tests were created and what edge cases they cover.
                """.formatted(customGoal, taskMdInstruction, robotInstruction);

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
                writeHtmlReport(projectPath, "taika-report-gen-code.html", "Taika Code Generation Report", SwarmStatus.MAX_ITERATIONS_REACHED, "Execution failed in unit test phase: " + getRootCauseMessage(e), thread);
                return new SwarmResult(thread.getId(), SwarmStatus.MAX_ITERATIONS_REACHED, thread.getHistory(), "Execution failed in unit test phase: " + getRootCauseMessage(e));
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
                Unit tests (and any Robot tests) are passing with initial dummy code.%s%s%s
                1. Inspect all files for 'TODO' comments and dummy stubs.
                2. Replace all dummy implementations with full production-ready code matching context.md and api.md.
                3. Remove resolved 'TODO' comments.
                4. Ensure all unit tests AND any existing Robot Framework tests continue to pass. If an existing unit test asserted dummy-specific behavior, adjust the assertion to match the specification.
                5. When writing regex in Java, ensure valid backslash escaping (\\\\d, \\\\w).
                5. Use fileSystemWriter to write the updated production files.
                7. If task.md is present, synchronize README.md, context.md, and api.md with the new implementation.
                """.formatted(customGoal, taskMdInstruction, robotInstruction);

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
                writeHtmlReport(projectPath, "taika-report-gen-code.html", "Taika Code Generation Report", SwarmStatus.MAX_ITERATIONS_REACHED, "Execution failed in coder phase: " + getRootCauseMessage(e), thread);
                return new SwarmResult(thread.getId(), SwarmStatus.MAX_ITERATIONS_REACHED, thread.getHistory(), "Execution failed in coder phase: " + getRootCauseMessage(e));
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
                All unit tests pass and production code has been implemented.%s%s
                Verify that the implementation satisfies all requirements in context.md, api.md, and README.md (and task.md if present).
                If task.md is present, ensure README.md, api.md, and context.md have been updated to reflect the new state.
                If task.md is NOT present, ensure README.md, api.md, and context.md were NOT modified.
                Summarize:
                1. Requirement verification status against specification.
                2. Unit test suite and Robot test suite verification.
                3. Production components implemented.
                4. Verification status (all tests passing).
                """.formatted(customGoal, taskMdInstruction);

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
            writeHtmlReport(projectPath, "taika-report-gen-code.html", "Taika Code Generation Report", SwarmStatus.MAX_ITERATIONS_REACHED, "Execution failed in validator phase: " + getRootCauseMessage(e), thread);
            return new SwarmResult(thread.getId(), SwarmStatus.MAX_ITERATIONS_REACHED, thread.getHistory(), "Execution failed in validator phase: " + getRootCauseMessage(e));
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
                Path nodeModules = projectPath.resolve("node_modules");
                if (!Files.exists(nodeModules)) {
                    logger.info("node_modules not found. Running 'npm install' on: {}", projectPath);
                    runProcess(projectPath, "npm", "install");
                }
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

    private TestRunResult runProcess(Path directory, String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(directory.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append("\n");
            }
            int exitCode = process.waitFor();
            return new TestRunResult(exitCode == 0, output.toString());
        } catch (Exception e) {
            return new TestRunResult(false, e.getMessage());
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
        boolean taskMdPresent = Files.exists(projectPath.resolve("task.md"));
        String taskMdInstruction = taskMdPresent
                ? "\nTASK.MD ACTIVE: 'task.md' is present! Generate or update Robot Framework acceptance tests according to task.md requirements."
                : "";

        String taskDescription = """
                Read context.md and api.md.%s Generate a simple, clean, and declarative Robot Framework test file (*.robot) for acceptance test scenarios (valid and invalid cases).
                Write the file to robots/credentials_validation.robot using the fileSystemWriter tool.
                RULES:
                - Keep tests simple and declarative using test cases and keywords.
                - Under *** Settings ***, explicitly import 'Library    Collections' and 'Library    String'.
                - Do NOT invent fake keywords like 'Should Be False' or 'Should Contain Key'. Use standard assertions: 'Should Be Equal', 'Should Be True'.
                - Do NOT import Java classes. Do NOT create .py files.
                """.formatted(taskMdInstruction);

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
                writeHtmlReport(projectPath, "taika-report-gen-robots.html", "Taika Robot Generation Report", SwarmStatus.MAX_ITERATIONS_REACHED, "Execution failed: " + getRootCauseMessage(e), thread);
                return new SwarmResult(thread.getId(), SwarmStatus.MAX_ITERATIONS_REACHED, thread.getHistory(), "Execution failed: " + getRootCauseMessage(e));
            }
        }

        String failSummary = String.format("Robot test generation and dry-run validation did not succeed within %d iterations.", maxLoops);
        writeHtmlReport(projectPath, "taika-report-gen-robots.html", "Taika Robot Generation Report", SwarmStatus.MAX_ITERATIONS_REACHED, failSummary, thread);
        return new SwarmResult(thread.getId(), SwarmStatus.MAX_ITERATIONS_REACHED, thread.getHistory(),
                failSummary);
    }

    private String getRootCauseMessage(Throwable throwable) {
        if (throwable == null) return "Unknown error";
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() != null && !root.getMessage().isBlank()
                ? root.getMessage()
                : throwable.getMessage();
    }

    void writeHtmlReport(Path projectPath, String fileName, String title, SwarmStatus status, String content, ExecutionThread thread) {
        try {
            Path reportFile = projectPath.resolve(fileName);
            String statusColor = (status == SwarmStatus.SUCCESS) ? "#10b981" : "#ef4444";
            String sanitizedContent = content
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\n", "<br/>");

            StringBuilder historyHtml = new StringBuilder();
            int historyCount = 0;
            if (thread != null && thread.getHistory() != null) {
                historyCount = thread.getHistory().size();
                for (AgentInvocationLog log : thread.getHistory()) {
                    historyHtml.append(String.format("<li><strong>%s</strong>: %s</li>", log.type(), log.status()));
                }
            }
            if (historyHtml.isEmpty()) {
                historyHtml.append("<li>No agent invocations recorded</li>");
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String runCardHtml = String.format("""
                    <div class="run-card">
                        <div class="run-header">
                            <span class="timestamp">Execution Time: %s</span>
                            <span class="badge" style="background: %s;">%s</span>
                        </div>
                        <div class="history">
                            <strong>Agent Invocation Steps (%d):</strong>
                            <ul>%s</ul>
                        </div>
                        <div class="content">%s</div>
                    </div>
                    """, timestamp, statusColor, status, historyCount, historyHtml, sanitizedContent);

            String placeholder = "<!-- RUN_ENTRIES_PLACEHOLDER -->";
            String html;

            if (Files.exists(reportFile)) {
                String existingHtml = Files.readString(reportFile);
                if (existingHtml.contains(placeholder)) {
                    html = existingHtml.replace(placeholder, runCardHtml + "\n            " + placeholder);
                } else if (existingHtml.contains("<footer>")) {
                    html = existingHtml.replace("<footer>", runCardHtml + "\n            " + placeholder + "\n            <footer>");
                } else if (existingHtml.contains("</div>\n</body>")) {
                    html = existingHtml.replace("</div>\n</body>", runCardHtml + "\n            " + placeholder + "\n        </div>\n</body>");
                } else if (existingHtml.contains("</body>")) {
                    html = existingHtml.replace("</body>", runCardHtml + "\n            " + placeholder + "\n</body>");
                } else {
                    html = existingHtml + "\n" + runCardHtml;
                }
                logger.info("Appended new execution run (timestamp: {}) to HTML report: {}", timestamp, reportFile);
            } else {
                html = String.format("""
                        <!DOCTYPE html>
                        <html lang="en">
                        <head>
                            <meta charset="UTF-8">
                            <title>%s</title>
                            <style>
                                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background: #f8fafc; color: #1e293b; margin: 0; padding: 2rem; }
                                .container { max-width: 900px; margin: 0 auto; background: #ffffff; border-radius: 8px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1); padding: 2rem; }
                                h1 { margin-top: 0; font-size: 1.75rem; color: #0f172a; border-bottom: 2px solid #e2e8f0; padding-bottom: 0.75rem; }
                                .run-card { border-bottom: 2px solid #e2e8f0; padding-bottom: 2rem; margin-bottom: 2rem; }
                                .run-card:last-of-type { border-bottom: none; margin-bottom: 0; }
                                .run-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
                                .timestamp { font-size: 0.95rem; font-weight: 600; color: #64748b; }
                                .badge { display: inline-block; padding: 0.35rem 0.75rem; border-radius: 9999px; font-weight: 600; color: #ffffff; }
                                .history { background: #f1f5f9; padding: 1rem 1.5rem; border-radius: 6px; margin-bottom: 1.5rem; }
                                .history ul { margin: 0.5rem 0 0 0; padding-left: 1.2rem; }
                                .content { background: #0f172a; color: #e2e8f0; padding: 1.5rem; border-radius: 6px; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; font-size: 0.9rem; line-height: 1.6; overflow-x: auto; }
                                footer { margin-top: 2rem; font-size: 0.8rem; color: #64748b; text-align: center; }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <h1>%s</h1>
                                %s
                                %s
                                <footer>Generated by Taika Swarm Orchestrator</footer>
                            </div>
                        </body>
                        </html>
                        """, title, title, runCardHtml, placeholder);
                logger.info("Created new HTML report with initial run (timestamp: {}): {}", timestamp, reportFile);
            }

            Files.writeString(reportFile, html);
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
        Path dedicatedRobotsDir = projectPath.resolve("robots");
        if (Files.isDirectory(dedicatedRobotsDir)) {
            try (Stream<Path> stream = Files.walk(dedicatedRobotsDir, 3)) {
                Path found = stream.filter(p -> p.toString().endsWith(".robot")).findFirst().orElse(null);
                if (found != null) return found;
            } catch (Exception ignored) {}
        }

        try (Stream<Path> stream = Files.walk(projectPath, 10)) {
            return stream
                    .filter(p -> !p.toString().contains("/target/") && !p.toString().contains("/.git/") && !p.toString().contains("/node_modules/"))
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
            case AUDITOR:
                return new StateTransition(null, "Audit complete.");
            default:
                throw new IllegalStateException("Unhandled agent type in state transition: " + currentAgent);
        }
    }
}