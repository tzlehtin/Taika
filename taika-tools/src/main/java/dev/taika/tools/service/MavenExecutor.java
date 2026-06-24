package dev.taika.tools.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class MavenExecutor {

    /**
     * Executes Maven commands in a specified project directory.
     *
     * @param projectPath The path to the project directory where the command will be run.
     * @param goals The Maven goals to execute (e.g., "clean", "install").
     * @return The combined stdout and stderr from the process, mimicking terminal output.
     * @throws IOException If an I/O error occurs.
     * @throws InterruptedException If the current thread is interrupted while waiting.
     */
    public String execute(Path projectPath, String... goals) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("mvn");
        command.addAll(Arrays.asList(goals));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(projectPath.toFile());
        processBuilder.redirectErrorStream(true); // Combine stdout and stderr

        Process process = processBuilder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new InterruptedException("Maven process timed out and was forcibly destroyed.");
        }

        return output.toString();
    }
}