package dev.taika.tools.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemWriterTest {

    private FileSystemWriter fileSystemWriter;

    @BeforeEach
    void setUp() {
        fileSystemWriter = new FileSystemWriter();
    }

    @Test
    void shouldWriteContentToFileAndCreateDirectories(@TempDir Path tempDir) throws IOException {
        Path newFile = tempDir.resolve("subdir/test.txt");
        String content = "This is a test.";

        fileSystemWriter.write(newFile, content);

        assertTrue(Files.exists(newFile));
        assertEquals(content, Files.readString(newFile));
    }
}