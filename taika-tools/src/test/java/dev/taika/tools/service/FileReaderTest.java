package dev.taika.tools.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileReaderTest {

    private FileReader fileReader;

    @BeforeEach
    void setUp() {
        fileReader = new FileReader();
    }

    @Test
    void shouldReadFileContentCorrectly(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.md");
        String expectedContent = "Hello, Taika!";
        Files.writeString(file, expectedContent);

        var request = new FileReader.ReadFileRequest(file.toString());
        String actualContent = fileReader.readFile(request);

        assertEquals(expectedContent, actualContent);
    }

    @Test
    void shouldThrowIOExceptionForNonExistentFile(@TempDir Path tempDir) {
        Path nonExistentFile = tempDir.resolve("nonexistent.txt");
        var request = new FileReader.ReadFileRequest(nonExistentFile.toString());
        assertThrows(IOException.class, () -> fileReader.readFile(request));
    }
}