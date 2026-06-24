package dev.taika.tools.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class FileReader {

    /**
     * Reads the content of a single file from a given path.
     *
     * @param filePath The path to the file to be read.
     * @return The content of the file as a String.
     * @throws IOException If an I/O error occurs or the path is not a regular file.
     */
    public String readFile(Path filePath) throws IOException {
        return Files.readString(filePath);
    }
}