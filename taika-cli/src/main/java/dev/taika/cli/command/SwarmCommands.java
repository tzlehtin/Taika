package dev.taika.cli.command;

import dev.taika.swarm.orchestration.SwarmOrchestrator;
import dev.taika.swarm.orchestration.model.SwarmResult;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.nio.file.Files;
import java.nio.file.Path;

@ShellComponent("Taika Swarm Commands")
public class SwarmCommands {

    private final SwarmOrchestrator swarmOrchestrator;

    public SwarmCommands(SwarmOrchestrator swarmOrchestrator) {
        this.swarmOrchestrator = swarmOrchestrator;
    }

    @ShellMethod(key = {"gen-code", "code"}, value = "Generates unit tests, dummy stubs, and production code following deterministic TDD loops.")
    public String generateCode(
            @ShellOption(value = {"--path", "-p"}, help = "The absolute or relative path to the root directory of the project.") String path,
            @ShellOption(value = {"--max-loops", "-m"}, defaultValue = "8", help = "Maximum self-healing compile/test iterations per phase.") int maxLoops,
            @ShellOption(value = {"--objective", "-o"}, defaultValue = ShellOption.NULL, help = "Optional custom objective or feature request.") String objective
    ) {
        System.out.println("Starting Code generation with deterministic TDD loops:");
        System.out.println("Project Path: " + path);
        System.out.println("Max Loops: " + maxLoops);
        if (objective != null && !objective.isBlank()) {
            System.out.println("Objective: " + objective);
        }
        Path taskPath = Path.of(path).toAbsolutePath().normalize().resolve("task.md");
        if (Files.exists(taskPath)) {
            System.out.println("Task Mode: ACTIVE (found task.md - specifications will be synchronized)");
        }
        System.out.println("-------------------------------------------------");

        try {
            Path projectPath = Path.of(path).toAbsolutePath().normalize();

            // Validate the path as required by context.md
            if (!Files.exists(projectPath)) {
                return "Error: The specified path does not exist: " + projectPath;
            }
            if (!Files.isDirectory(projectPath)) {
                return "Error: The specified path is not a directory: " + projectPath;
            }

            SwarmResult result = swarmOrchestrator.generateCode(projectPath, maxLoops, objective);

            return String.format("\nCode Generation Complete!\nStatus: %s\nReport File: %s\nSummary:\n%s\nHistory: %d agent invocations.",
                    result.status(), projectPath.resolve("taika-report-gen-code.html"),
                    result.finalSummary(), result.invocationHistory().size());
        } catch (Exception e) {
            return "An unexpected error occurred during code generation: " + e.getMessage();
        }
    }

    @ShellMethod(key = "execution", value = "Triggers the multi-agent swarm orchestration for a targeted local repository.")
    public String execution(
            @ShellOption(value = {"--path", "-p"}, help = "The absolute or relative path to the root directory of the project.") String path,
            @ShellOption(value = {"--objective", "-o"}, help = "The high-level instruction or business problem that the swarm needs to solve.") String objective
    ) {
        System.out.println("Starting Taika swarm with the following parameters:");
        System.out.println("Project Path: " + path);
        System.out.println("Objective: " + objective);
        System.out.println("-------------------------------------------------");

        try {
            Path projectPath = Path.of(path).toAbsolutePath().normalize();

            // Validate the path as required by context.md
            if (!Files.exists(projectPath)) {
                return "Error: The specified path does not exist: " + projectPath;
            }
            if (!Files.isDirectory(projectPath)) {
                return "Error: The specified path is not a directory: " + projectPath;
            }

            SwarmResult result = swarmOrchestrator.orchestrate(projectPath, objective);
            return String.format("\nOrchestration Complete!\nStatus: %s\nSummary: %s\nHistory: %d agent invocations.",
                    result.status(), result.finalSummary(), result.invocationHistory().size());
        } catch (Exception e) {
            return "An unexpected error occurred during orchestration: " + e.getMessage();
        }
    }

    public String audit(String path, String maxLoops) {
        return audit(path, maxLoops, false);
    }

    @ShellMethod(key = "audit", value = "Performs deep architectural, code quality, and test effectiveness audit, generating taika-report-audit.html and task.md.")
    public String audit(
            @ShellOption(value = {"--path", "-p"}, arity = 1, help = "The absolute or relative path to the root directory of the project.") String path,
            @ShellOption(value = {"--max-loops", "-m"}, defaultValue = ShellOption.NULL, help = "Optional max loops (ignored in audit).") String maxLoops,
            @ShellOption(value = {"--scan-vulnerabilities", "--security", "-s"}, defaultValue = "false", help = "Include deep dependency vulnerability scanning (OWASP / npm audit).") boolean scanVulnerabilities
    ) {
        System.out.println("Starting Taika Project Audit:");
        System.out.println("Project Path: " + path);
        if (scanVulnerabilities) {
            System.out.println("Security Scan: ENABLED (OWASP Dependency-Check / npm audit active)");
        }
        System.out.println("-------------------------------------------------");

        try {
            Path projectPath = Path.of(path).toAbsolutePath().normalize();

            if (!Files.exists(projectPath)) {
                return "Error: The specified path does not exist: " + projectPath;
            }
            if (!Files.isDirectory(projectPath)) {
                return "Error: The specified path is not a directory: " + projectPath;
            }

            SwarmResult result = scanVulnerabilities
                    ? swarmOrchestrator.audit(projectPath, true)
                    : swarmOrchestrator.audit(projectPath);
            return String.format("\nAudit Complete!\nStatus: %s\nReport File: %s\nTask File: %s\nSummary:\n%s",
                    result.status(),
                    projectPath.resolve("taika-report-audit.html"),
                    projectPath.resolve("task.md"),
                    result.finalSummary());
        } catch (Exception e) {
            return "An unexpected error occurred during audit: " + e.getMessage();
        }
    }

    @ShellMethod(key = {"gen-robots", "robot-tests"}, value = "Generates Robot Framework test suites based on context.md and api.md.")
    public String generateRobotTests(
            @ShellOption(value = {"--path", "-p"}, help = "The absolute or relative path to the root directory of the project.") String path,
            @ShellOption(value = {"--max-loops", "-m"}, defaultValue = "8", help = "Maximum dry-run self-healing iterations.") int maxLoops
    ) {
        System.out.println("Starting Robot test suite generation:");
        System.out.println("Project Path: " + path);
        System.out.println("Max Loops: " + maxLoops);
        System.out.println("-------------------------------------------------");

        try {
            Path projectPath = Path.of(path).toAbsolutePath().normalize();

            if (!Files.exists(projectPath)) {
                return "Error: The specified path does not exist: " + projectPath;
            }
            if (!Files.isDirectory(projectPath)) {
                return "Error: The specified path is not a directory: " + projectPath;
            }

            SwarmResult result = swarmOrchestrator.generateRobotTests(projectPath, maxLoops);
            return String.format("\nRobot Test Generation Complete!\nStatus: %s\nReport File: %s\nSummary:\n%s",
                    result.status(), projectPath.resolve("taika-report-gen-robots.html"), result.finalSummary());
        } catch (Exception e) {
            return "An unexpected error occurred during Robot test generation: " + e.getMessage();
        }
    }

    public String pipeline(String path, int maxLoops, String objective) {
        return pipeline(path, maxLoops, objective, false);
    }

    @ShellMethod(key = {"pipeline", "flow", "auto"}, value = "Executes the full automated workflow: gen-robots -> gen-code -> audit in sequence.")
    public String pipeline(
            @ShellOption(value = {"--path", "-p"}, arity = 1, help = "The absolute or relative path to the root directory of the project.") String path,
            @ShellOption(value = {"--max-loops", "-m"}, defaultValue = "8", help = "Maximum iterations per phase.") int maxLoops,
            @ShellOption(value = {"--objective", "-o"}, defaultValue = ShellOption.NULL, help = "Optional custom objective or feature request.") String objective,
            @ShellOption(value = {"--scan-vulnerabilities", "--security", "-s"}, defaultValue = "false", help = "Include deep dependency vulnerability scanning (OWASP / npm audit).") boolean scanVulnerabilities
    ) {
        System.out.println("Starting Automated Taika Pipeline (gen-robots -> gen-code -> audit):");
        System.out.println("Project Path: " + path);
        System.out.println("Max Loops: " + maxLoops);
        if (objective != null && !objective.isBlank()) {
            System.out.println("Objective: " + objective);
        }
        Path taskPath = Path.of(path).toAbsolutePath().normalize().resolve("task.md");
        if (Files.exists(taskPath)) {
            System.out.println("Task Mode: ACTIVE (found task.md - specifications will be synchronized)");
        }
        if (scanVulnerabilities) {
            System.out.println("Security Scan: ENABLED (OWASP / npm audit active in audit phase)");
        }
        System.out.println("-------------------------------------------------");

        try {
            Path projectPath = Path.of(path).toAbsolutePath().normalize();

            if (!Files.exists(projectPath)) {
                return "Error: The specified path does not exist: " + projectPath;
            }
            if (!Files.isDirectory(projectPath)) {
                return "Error: The specified path is not a directory: " + projectPath;
            }

            SwarmResult result = scanVulnerabilities
                    ? swarmOrchestrator.pipeline(projectPath, maxLoops, objective, true)
                    : swarmOrchestrator.pipeline(projectPath, maxLoops, objective);
            return String.format("\nPipeline Complete!\nStatus: %s\nSummary:\n%s",
                    result.status(), result.finalSummary());
        } catch (Exception e) {
            return "An unexpected error occurred during pipeline execution: " + e.getMessage();
        }
    }

    @ShellMethod(key = {"security-scan", "owasp-scan"}, value = "Performs deep dependency vulnerability scanning (OWASP Dependency-Check / npm audit).")
    public String securityScan(
            @ShellOption(value = {"--path", "-p"}, arity = 1, help = "The absolute or relative path to the root directory of the project.") String path
    ) {
        System.out.println("Starting Taika Dependency Security Scan (OWASP / npm audit):");
        System.out.println("Project Path: " + path);
        System.out.println("-------------------------------------------------");

        try {
            Path projectPath = Path.of(path).toAbsolutePath().normalize();

            if (!Files.exists(projectPath)) {
                return "Error: The specified path does not exist: " + projectPath;
            }
            if (!Files.isDirectory(projectPath)) {
                return "Error: The specified path is not a directory: " + projectPath;
            }

            String result = swarmOrchestrator.scanVulnerabilities(projectPath);
            return "\nSecurity Scan Complete!\n\n" + result;
        } catch (Exception e) {
            return "An unexpected error occurred during security scan: " + e.getMessage();
        }
    }
}