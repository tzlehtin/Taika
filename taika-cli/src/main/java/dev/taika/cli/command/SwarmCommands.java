package dev.taika.cli.command;

import dev.taika.swarm.orchestration.SwarmOrchestrator;
import dev.taika.swarm.orchestration.model.SwarmResult;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.nio.file.Path;

@ShellComponent("Taika Swarm Commands")
public class SwarmCommands {

    private final SwarmOrchestrator swarmOrchestrator;

    public SwarmCommands(SwarmOrchestrator swarmOrchestrator) {
        this.swarmOrchestrator = swarmOrchestrator;
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
            Path projectPath = Path.of(path).toAbsolutePath();
            SwarmResult result = swarmOrchestrator.orchestrate(projectPath, objective);

            // Format and return a simple summary
            return String.format("\nOrchestration Complete!\nStatus: %s\nSummary: %s\nHistory: %d agent invocations.",
                    result.status(), result.finalSummary(), result.invocationHistory().size());
        } catch (Exception e) {
            return "An unexpected error occurred during orchestration: " + e.getMessage();
        }
    }
}