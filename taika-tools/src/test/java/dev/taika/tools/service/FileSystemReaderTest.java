package dev.taika.tools.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
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
        var request = new FileSystemReader.ReadRequest(tempDir.toAbsolutePath().toString(), new String[]{"*.java", "*.md"});
        Map<String, String> result = fileSystemReader.readAll(request);

        // Assert
        assertEquals(3, result.size());
        assertTrue(result.containsKey(javaFile.toAbsolutePath().toString()));
        assertTrue(result.containsKey(mdFile.toAbsolutePath().toString()));
        assertTrue(result.containsKey(contextFile.toAbsolutePath().toString()));
        assertEquals("# API", result.get(mdFile.toAbsolutePath().toString()));
    }

    /**
     * This test covers the specific case where reading a file fails and ensures
     * the IOException is correctly wrapped in a RuntimeException.
     */
    @Test
    void readFileContent_shouldWrapIOExceptionInRuntimeException(@TempDir Path tempDir) throws Exception {
        // Setup: Create a directory. Trying to read a directory as a file will throw an IOException.
        Path directoryPath = tempDir.resolve("a_directory");
        Files.createDirectory(directoryPath);

        // Use reflection to make the private method accessible for testing.
        Method readFileContentMethod = FileSystemReader.class.getDeclaredMethod("readFileContent", Path.class);
        readFileContentMethod.setAccessible(true);

        // Act & Assert: Invoke the private method and verify that it throws the expected exception.
        Exception exception = assertThrows(Exception.class, () -> {
            try {
                readFileContentMethod.invoke(fileSystemReader, directoryPath);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw (Exception) e.getTargetException(); // Unwrap the actual exception
            }
        });

        assertEquals(RuntimeException.class, exception.getClass());
        assertTrue(exception.getMessage().startsWith("Failed to read file:"));
        assertTrue(exception.getCause() instanceof IOException);
    }
}