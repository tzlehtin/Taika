package dev.taika.agent.config;

import dev.taika.tools.service.FileReader;
import dev.taika.tools.service.FileSystemReader;
import dev.taika.tools.service.FileSystemWriter;
import dev.taika.tools.service.MavenExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolConfigurationTest {

    @Mock
    private FileSystemReader fileSystemReader;
    @Mock
    private FileSystemWriter fileSystemWriter;
    @Mock
    private MavenExecutor mavenExecutor;
    @Mock
    private FileReader fileReader;

    private ToolConfiguration toolConfiguration;

    @BeforeEach
    void setUp() {
        toolConfiguration = new ToolConfiguration();
    }

    @Test
    void fileSystemReaderFunction_shouldCallServiceAndHandleException() throws IOException {
        // Arrange
        Function<FileSystemReader.ReadRequest, Map<String, String>> function = toolConfiguration.fileSystemReaderFunction(fileSystemReader);
        var request = new FileSystemReader.ReadRequest("/fake", new String[]{"*.java"});

        // Act
        function.apply(request);

        // Assert
        verify(fileSystemReader).readAll(request);

        // Arrange for exception
        when(fileSystemReader.readAll(any())).thenThrow(new IOException("Test Exception"));

        // Act & Assert for exception
        assertThrows(RuntimeException.class, () -> function.apply(request));
    }

    @Test
    void fileSystemWriterFunction_shouldCallServiceAndHandleException() throws IOException {
        // Arrange
        Function<FileSystemWriter.WriteRequest, String> function = toolConfiguration.fileSystemWriterFunction(fileSystemWriter);
        var request = new FileSystemWriter.WriteRequest("/fake/file.txt", "content");

        // Act
        function.apply(request);

        // Assert
        verify(fileSystemWriter).write(request);

        // Arrange for exception
        when(fileSystemWriter.write(any())).thenThrow(new IOException("Test Exception"));

        // Act & Assert for exception
        assertThrows(RuntimeException.class, () -> function.apply(request));
    }

    @Test
    void mavenExecutorFunction_shouldCallServiceAndHandleException() throws Exception {
        // Arrange
        Function<MavenExecutor.ExecuteRequest, String> function = toolConfiguration.mavenExecutorFunction(mavenExecutor);
        var request = new MavenExecutor.ExecuteRequest("/fake", new String[]{"clean"});

        // Act
        function.apply(request);

        // Assert
        verify(mavenExecutor).execute(request);

        // Arrange for exception
        when(mavenExecutor.execute(any())).thenThrow(new InterruptedException("Test Exception"));

        // Act & Assert for exception
        assertThrows(RuntimeException.class, () -> function.apply(request));
    }

    @Test
    void fileReaderFunction_shouldCallServiceAndHandleException() throws IOException {
        // Arrange
        Function<FileReader.ReadFileRequest, String> function = toolConfiguration.fileReaderFunction(fileReader);
        var request = new FileReader.ReadFileRequest("/fake/file.txt");

        // Act
        function.apply(request);

        // Assert
        verify(fileReader).readFile(request);

        // Arrange for exception
        when(fileReader.readFile(any())).thenThrow(new IOException("Test Exception"));

        // Act & Assert for exception
        assertThrows(RuntimeException.class, () -> function.apply(request));
    }
}