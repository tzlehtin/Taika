package dev.taika.tools.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;

@Service
public class MavenExecutor {

    private final long timeout;
    private final TimeUnit unit;
    // Allow injecting a ProcessBuilder for testing
    private final Callable<ProcessBuilder> processBuilderFactory;

    public MavenExecutor() {
        this(5, TimeUnit.MINUTES, ProcessBuilder::new);
    }

    // Constructor for testing purposes
    MavenExecutor(long timeout, TimeUnit unit, Callable<ProcessBuilder> processBuilderFactory) {
        this.timeout = timeout;
        this.unit = unit;
        this.processBuilderFactory = processBuilderFactory;
    }

    // Overloaded constructor for simpler test setup
    MavenExecutor(long timeout, TimeUnit unit) {
        this(timeout, unit, ProcessBuilder::new);
    }

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
        ProcessBuilder processBuilder;
        try {
            processBuilder = processBuilderFactory.call();
        } catch (Exception e) {
            throw new IOException("Failed to create ProcessBuilder", e);
        }

        List<String> command = new ArrayList<>(goals.length + 1);
        command.add("mvn");
        command.addAll(List.of(goals));

        processBuilder.command(command);
        processBuilder.directory(projectPath.toFile());
        processBuilder.redirectErrorStream(true); // Combine stdout and stderr

        Process process = processBuilder.start();

        // Read the output stream on a separate thread to prevent blocking.
        StringBuilder output = new StringBuilder();
        final AtomicReference<IOException> readerException = new AtomicReference<>();
        Thread readerThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            } catch (IOException e) {
                // Capture the exception so the main thread can inspect it.
                // This is expected when the process is forcibly destroyed.
                readerException.set(e);
            }
        });
        readerThread.start();

        boolean finished = process.waitFor(this.timeout, this.unit);
        if (!finished) {
            readerThread.interrupt();
            process.destroyForcibly();
            throw new InterruptedException("Maven process timed out and was forcibly destroyed.");
        }

        // Wait for the reader thread to finish capturing all output.
        readerThread.join();

        // If the reader thread caught an exception during normal execution, rethrow it.
        if (readerException.get() != null) {
            throw readerException.get();
        }

        return output.toString();
    }
}