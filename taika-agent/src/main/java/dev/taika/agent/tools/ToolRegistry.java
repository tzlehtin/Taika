package dev.taika.agent.tools;

import dev.taika.tools.service.FileReader;
import dev.taika.tools.service.FileSystemReader;
import dev.taika.tools.service.FileSystemWriter;
import dev.taika.tools.service.MavenExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;

/**
 * This component discovers all `taika-tools` services and wraps them as
 * Spring AI `FunctionCallback` instances, making them available to the `ChatClient`.
 */
// @Component annotation removed to allow for dynamic instantiation with projectPath
public class ToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ToolRegistry.class);

    private final List<ToolCallback> toolCallbacks;

    public ToolRegistry(FileSystemReader fileSystemReader,
                        FileSystemWriter fileSystemWriter,
                        MavenExecutor mavenExecutor,
                        FileReader fileReader,
                        Path projectPath) { // Add projectPath to the constructor

        this.toolCallbacks = new ArrayList<>();

        // Wrap each tool service as a FunctionCallback and add it to the registry.
        toolCallbacks.add(FunctionToolCallback.builder("fileSystemReader", (FileSystemReader.ReadRequest request) -> {
                    // Ensure the root path for reading is always within the project path
                    Path safePath = projectPath.resolve(request.rootPath()).normalize();
                    logger.info("TOOL CALL: fileSystemReader | Safe Path: {}", safePath);
                    try {
                        return fileSystemReader.readAll(new FileSystemReader.ReadRequest(safePath.toString(), request.globPatterns()));
                    } catch (IOException e) {
                        logger.error("TOOL ERROR: fileSystemReader failed.", e);
                        throw new RuntimeException(e);
                    }
                })
                .description("Recursively finds and reads files matching glob patterns.")
                .inputType(FileSystemReader.ReadRequest.class).build());

        toolCallbacks.add(FunctionToolCallback.builder("fileSystemWriter", (FileSystemWriter.WriteRequest request) -> {
                    // CRITICAL FIX: Resolve the AI-generated path against the absolute project path.
                    Path absolutePath = projectPath.resolve(request.filePath()).normalize();
                    logger.info("TOOL CALL: fileSystemWriter | Absolute Path: {}", absolutePath);
                    try {
                        // Create a new, safe request with the absolute path.
                        return fileSystemWriter.write(new FileSystemWriter.WriteRequest(absolutePath.toString(), request.content()));
                    } catch (IOException e) {
                        logger.error("TOOL ERROR: fileSystemWriter failed.", e);
                        throw new RuntimeException(e);
                    }
                })
                .description("Writes or overwrites content to a specified file.")
                .inputType(FileSystemWriter.WriteRequest.class).build());

        toolCallbacks.add(FunctionToolCallback.builder("mavenExecutor", (MavenExecutor.ExecuteRequest request) -> {
                    // CRITICAL FIX: Ensure maven always runs in the correct project directory.
                    Path absolutePath = projectPath.resolve(request.projectPath()).normalize();
                    logger.info("TOOL CALL: mavenExecutor | Absolute Path: {} | Goals: {}", absolutePath, String.join(" ", request.goals()));
                    try {
                        // Create a new, safe request with the absolute path.
                        return mavenExecutor.execute(new MavenExecutor.ExecuteRequest(absolutePath.toString(), request.goals()));
                    } catch (IOException | InterruptedException e) {
                        logger.error("TOOL ERROR: mavenExecutor failed.", e);
                        throw new RuntimeException(e);
                    }
                })
                .description("Executes Maven commands in a specified project directory.")
                .inputType(MavenExecutor.ExecuteRequest.class).build());

        toolCallbacks.add(FunctionToolCallback.builder("fileReader", (FileReader.ReadFileRequest request) -> {
                    // CRITICAL FIX: Resolve the AI-generated path against the absolute project path.
                    Path absolutePath = projectPath.resolve(request.filePath()).normalize();
                    logger.info("TOOL CALL: fileReader | Absolute Path: {}", absolutePath);
                    try {
                        // Create a new, safe request with the absolute path.
                        return fileReader.readFile(new FileReader.ReadFileRequest(absolutePath.toString()));
                    } catch (IOException e) {
                        logger.error("TOOL ERROR: fileReader failed.", e);
                        throw new RuntimeException(e);
                    }
                })
                .description("Reads the content of a single file.")
                .inputType(FileReader.ReadFileRequest.class).build());
    }

    public List<ToolCallback> getToolCallbacks() {
        return toolCallbacks;
    }
}