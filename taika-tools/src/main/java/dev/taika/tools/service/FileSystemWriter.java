package dev.taika.tools.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class FileSystemWriter {

    public record WriteRequest(
            @JsonProperty(required = true, value = "filePath") String filePath,
            @JsonProperty(required = true, value = "content") String content
    ) {}

    /**
     * Writes or overwrites content to a specified file.
     *
     * @param request The DTO containing the file path and content.
     * @throws IOException If an I/O error occurs.
     */
    public String write(WriteRequest request) throws IOException {
        Path filePath = Path.of(request.filePath());
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, request.content());
        return "Successfully wrote to " + request.filePath();
    }
}