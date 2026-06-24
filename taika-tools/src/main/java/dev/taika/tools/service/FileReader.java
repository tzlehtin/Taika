package dev.taika.tools.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class FileReader {

    public record ReadFileRequest(
            @JsonProperty(required = true, value = "filePath") String filePath
    ) {}

    /**
     * Reads the content of a single file from a given path.
     *
     * @param request The DTO containing the path to the file to be read.
     * @return The content of the file as a String.
     * @throws IOException If an I/O error occurs or the path is not a regular file.
     */
    public String readFile(ReadFileRequest request) throws IOException {
        return Files.readString(Path.of(request.filePath()));
    }
}