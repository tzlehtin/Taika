package dev.taika.tools.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FileSystemReader {

    /**
     * This is the DTO that Spring AI will convert into a JSON schema for the AI model.
     * The AI will fill this object's fields.
     */
    public record ReadRequest(
            @JsonProperty(required = true, value = "rootPath") String rootPath,
            @JsonProperty(required = true, value = "globPatterns") String[] globPatterns
    ) {}

    /**
     * Recursively finds and reads files matching glob patterns.
     *
     * @param request The DTO containing the root path and glob patterns.
     * @return A map where the key is the file's path as a String and the value is its content.
     * @throws IOException If an I/O error occurs.
     */
    public Map<String, String> readAll(ReadRequest request) throws IOException {
        Path root = Path.of(request.rootPath());
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**{" + String.join(",", request.globPatterns()) + "}");

        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(matcher::matches)
                    .collect(Collectors.toMap(
                            path -> path.toString(), // Convert Path to String for the AI
                            this::readFileContent
                    ));
        }
    }

    // Extracted for better testability
    private String readFileContent(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + path, e);
        }
    }
}