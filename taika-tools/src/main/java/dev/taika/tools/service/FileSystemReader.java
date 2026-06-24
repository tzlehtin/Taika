package dev.taika.tools.service;

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
     * Recursively finds and reads files matching glob patterns.
     *
     * @param rootPath The directory to start scanning from.
     * @param globPatterns The glob patterns to match files against (e.g., "*.java", "context.md").
     * @return A map where the key is the file's Path and the value is its content as a String.
     * @throws IOException If an I/O error occurs.
     */
    public Map<Path, String> readAll(Path rootPath, String... globPatterns) throws IOException {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**{" + String.join(",", globPatterns) + "}");

        try (Stream<Path> walk = Files.walk(rootPath)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(matcher::matches)
                    .collect(Collectors.toMap(
                            path -> path,
                            this::readFileContent
                    ));
        }
    }

    // Extracted for better testability
    String readFileContent(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + path, e);
        }
    }
}