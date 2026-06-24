package dev.taika.tools.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class FileSystemWriter {

    /**
     * Writes or overwrites content to a specified file.
     *
     * @param filePath The path to the file to be written.
     * @param content The string content to write to the file.
     * @throws IOException If an I/O error occurs.
     */
    public void write(Path filePath, String content) throws IOException {
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
}