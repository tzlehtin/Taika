package dev.taika.tools.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemReaderTest {

    private FileSystemReader fileSystemReader;

    @BeforeEach
    void setUp() {
        fileSystemReader = new FileSystemReader();
    }

    @Test
    void shouldReadAllFilesMatchingGlobPatterns(@TempDir Path tempDir) throws IOException {
        // Create test files and directories
        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile, "public class Test {}");

        Path mdFile = tempDir.resolve("api.md");
        Files.writeString(mdFile, "# API");

        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        Path contextFile = subDir.resolve("context.md");
        Files.writeString(contextFile, "## Context");

        Files.createFile(tempDir.resolve("ignored.txt"));

        // Execute
        Map<Path, String> result = fileSystemReader.readAll(tempDir, "*.java", "*.md");

        // Assert
        assertEquals(3, result.size());
        assertTrue(result.containsKey(javaFile));
        assertTrue(result.containsKey(mdFile));
        assertTrue(result.containsKey(contextFile));
        assertEquals("# API", result.get(mdFile));
    }

    /**
     * This test covers the specific case where reading a file fails and ensures
     * the IOException is correctly wrapped in a RuntimeException.
     */
    @Test
    void readFileContent_shouldWrapIOExceptionInRuntimeException(@TempDir Path tempDir) throws IOException {
        // Setup: Create a directory. Trying to read a directory as a string throws an IOException.
        Path directoryPath = tempDir.resolve("a_directory");
        Files.createDirectory(directoryPath);

        // Execute and assert that the correct exception is thrown
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fileSystemReader.readFileContent(directoryPath));

        // Verify the exception details
        assertTrue(exception.getMessage().contains("Failed to read file"));
        assertTrue(exception.getCause() instanceof IOException);
    }
}