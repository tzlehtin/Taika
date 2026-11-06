package dev.taika.agent.tools;

import dev.taika.api.AgentType;
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
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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
    private final List<String> writtenFiles = new ArrayList<>();

    public ToolRegistry(FileSystemReader fileSystemReader,
                        FileSystemWriter fileSystemWriter,
                        MavenExecutor mavenExecutor,
                        FileReader fileReader,
                        Path projectPath) {
        this(fileSystemReader, fileSystemWriter, mavenExecutor, fileReader, projectPath, null);
    }

    public ToolRegistry(FileSystemReader fileSystemReader,
                        FileSystemWriter fileSystemWriter,
                        MavenExecutor mavenExecutor,
                        FileReader fileReader,
                        Path projectPath,
                        AgentType agentType) {

        this.toolCallbacks = new ArrayList<>();

        // VALIDATOR needs 0 tools (pure review and verification)
        if (agentType == AgentType.VALIDATOR) {
            return;
        }

        // ROBOT_TESTER only needs to write the .robot file (SwarmOrchestrator runs dry-run)
        if (agentType == AgentType.ROBOT_TESTER) {
            toolCallbacks.add(createWriterTool(fileSystemWriter, projectPath));
            return;
        }

        // AUDITOR only needs fileSystemWriter (to create task.md) and fileReader
        if (agentType == AgentType.AUDITOR) {
            toolCallbacks.add(createWriterTool(fileSystemWriter, projectPath));
            toolCallbacks.add(createSingleFileReaderTool(fileReader, projectPath));
            return;
        }

        // UNIT_TESTER and CODER only need writer (all existing files are already assembled into prompt context)
        // Eliminating fileReader prevents multi-turn prompt snowballing where the model re-reads already provided files.
        if (agentType == AgentType.UNIT_TESTER || agentType == AgentType.CODER) {
            toolCallbacks.add(createWriterTool(fileSystemWriter, projectPath));
            return;
        }

        // Default / null role: register standard tools (e.g. for testing)
        // Wrap each tool service as a FunctionCallback returning structured Map for Google GenAI JSON parsing.
        toolCallbacks.add(FunctionToolCallback.builder("fileSystemReader", (FileSystemReader.ReadRequest request) -> {
                    // Ensure the root path for reading is always within the project path
                    Path safePath = projectPath.resolve(request.rootPath()).normalize();
                    logger.info("TOOL CALL: fileSystemReader | Safe Path: {}", safePath);
                    try {
                        Map<String, String> rawFiles = fileSystemReader.readAll(new FileSystemReader.ReadRequest(safePath.toString(), request.globPatterns()));
                        Map<String, String> filteredFiles = new HashMap<>();
                        for (Map.Entry<String, String> entry : rawFiles.entrySet()) {
                            String key = entry.getKey();
                            if (!key.contains("/target/") && !key.contains("\\target\\")
                                    && !key.contains("/.git/") && !key.contains("\\.git\\")
                                    && !key.contains("/node_modules/") && !key.contains("\\node_modules\\")
                                    && !key.endsWith(".class") && !key.endsWith(".jar")) {
                                filteredFiles.put(key, entry.getValue());
                            }
                        }
                        return Map.of("status", "SUCCESS", "files", filteredFiles);
                    } catch (Exception e) {
                        logger.error("TOOL ERROR: fileSystemReader failed.", e);
                        return Map.of("status", "ERROR", "message", String.valueOf(e.getMessage()));
                    }
                })
                .description("Recursively finds and reads files matching glob patterns.")
                .inputType(FileSystemReader.ReadRequest.class).build());

        toolCallbacks.add(createWriterTool(fileSystemWriter, projectPath));
        toolCallbacks.add(createSingleFileReaderTool(fileReader, projectPath));
    }

    private ToolCallback createWriterTool(FileSystemWriter fileSystemWriter, Path projectPath) {
        return FunctionToolCallback.builder("fileSystemWriter", (FileSystemWriter.WriteRequest request) -> {
                    Path absolutePath = projectPath.resolve(request.filePath()).normalize();
                    logger.info("TOOL CALL: fileSystemWriter | Absolute Path: {}", absolutePath);
                    try {
                        writtenFiles.add(absolutePath.toString());
                        String writeResult = fileSystemWriter.write(new FileSystemWriter.WriteRequest(absolutePath.toString(), request.content()));
                        return Map.of("status", "SUCCESS", "message", writeResult);
                    } catch (Exception e) {
                        logger.error("TOOL ERROR: fileSystemWriter failed.", e);
                        return Map.of("status", "ERROR", "message", String.valueOf(e.getMessage()));
                    }
                })
                .description("Writes or overwrites content to a specified file.")
                .inputType(FileSystemWriter.WriteRequest.class).build();
    }

    private ToolCallback createSingleFileReaderTool(FileReader fileReader, Path projectPath) {
        return FunctionToolCallback.builder("fileReader", (FileReader.ReadFileRequest request) -> {
                    Path absolutePath = projectPath.resolve(request.filePath()).normalize();
                    logger.info("TOOL CALL: fileReader | Absolute Path: {}", absolutePath);
                    try {
                        String content = fileReader.readFile(new FileReader.ReadFileRequest(absolutePath.toString()));
                        return Map.of("status", "SUCCESS", "content", content);
                    } catch (Exception e) {
                        logger.error("TOOL ERROR: fileReader failed.", e);
                        return Map.of("status", "ERROR", "message", String.valueOf(e.getMessage()));
                    }
                })
                .description("Reads the content of a single file.")
                .inputType(FileReader.ReadFileRequest.class).build();
    }

    public List<ToolCallback> getToolCallbacks() {
        return toolCallbacks;
    }

    public List<String> getWrittenFiles() {
        return Collections.unmodifiableList(writtenFiles);
    }
}