package dev.taika.tools.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledOnOs({OS.LINUX, OS.MAC})
class MavenExecutorTest {

    private ProcessBuilder mockProcessBuilder;
    private Process mockProcess;

    @BeforeEach
    void setUp() {
        // Mock the Process and ProcessBuilder to control their behavior in tests
        mockProcessBuilder = mock(ProcessBuilder.class);
        mockProcess = mock(Process.class);
        // By default, chain the mocks together
        when(mockProcessBuilder.command(anyList())).thenReturn(mockProcessBuilder);
        when(mockProcessBuilder.directory(any())).thenReturn(mockProcessBuilder);
        when(mockProcessBuilder.redirectErrorStream(anyBoolean())).thenReturn(mockProcessBuilder);
        try {
            when(mockProcessBuilder.start()).thenReturn(mockProcess);
        } catch (IOException e) {
            fail("Setup failed", e);
        }
    }

    @Test
    @DisplayName("Should execute a command successfully and return its output")
    void shouldExecuteMavenVersionCommand(@TempDir Path tempDir) throws IOException, InterruptedException {
        MavenExecutor mavenExecutor = new MavenExecutor(); // Use real constructor
        var request = new MavenExecutor.ExecuteRequest(tempDir.toString(), new String[]{"--version"});
        String output = mavenExecutor.execute(request);

        assertNotNull(output);
        assertTrue(output.contains("Apache Maven"), "Output should contain 'Apache Maven'");
    }

    @Test
    @DisplayName("Should rethrow IOException from reader thread during normal execution")
    void shouldRethrowReaderIOException(@TempDir Path tempDir) throws InterruptedException, IOException {
        // Arrange:
        // 1. Process finishes successfully.
        when(mockProcess.waitFor(anyLong(), any())).thenReturn(true);

        // 2. But reading its output stream fails.
        InputStream mockInputStream = mock(InputStream.class);
        when(mockProcess.getInputStream()).thenReturn(mockInputStream);
        // This will cause the BufferedReader in the reader thread to throw an IOException.
        when(mockInputStream.read(any(), anyInt(), anyInt())).thenThrow(new IOException("Simulated read error"));

        MavenExecutor executor = new MavenExecutor(10, java.util.concurrent.TimeUnit.MILLISECONDS, () -> mockProcessBuilder);

        // Act & Assert: Expect the IOException from the reader thread to be re-thrown
        var request = new MavenExecutor.ExecuteRequest(tempDir.toString(), new String[]{"test"});
        assertThrows(IOException.class, () -> executor.execute(request));
    }

    @Test
    @DisplayName("Should throw IOException when ProcessBuilder factory fails")
    void shouldThrowIOExceptionWhenProcessBuilderFactoryFails(@TempDir Path tempDir) {
        // Arrange: Create an executor with a factory that throws an exception
        Exception factoryException = new Exception("Factory failed");
        MavenExecutor executor = new MavenExecutor(1, java.util.concurrent.TimeUnit.SECONDS, () -> {
            throw factoryException;
        });

        // Act & Assert
        var request = new MavenExecutor.ExecuteRequest(tempDir.toString(), new String[]{"test"});
        IOException thrown = assertThrows(IOException.class, () -> executor.execute(request));
        assertEquals("Failed to create ProcessBuilder", thrown.getMessage());
        assertEquals(factoryException, thrown.getCause());
    }

    @Test
    @DisplayName("Should throw InterruptedException and cover reader's IOException when process times out")
    void shouldThrowInterruptedExceptionWhenProcessTimesOut(@TempDir Path tempDir) throws InterruptedException, IOException {
        // Arrange:
        // 1. Simulate a process that never finishes.
        when(mockProcess.waitFor(anyLong(), any())).thenReturn(false);

        // 2. Simulate its input stream throwing an error when read (which happens after destroyForcibly).
        InputStream mockInputStream = mock(InputStream.class);
        when(mockProcess.getInputStream()).thenReturn(mockInputStream);
        when(mockInputStream.read(any(), anyInt(), anyInt())).thenThrow(new IOException("Stream closed on destroyed process"));

        MavenExecutor executor = new MavenExecutor(10, java.util.concurrent.TimeUnit.MILLISECONDS, () -> mockProcessBuilder);

        // Act & Assert: The main thread should throw InterruptedException.
        // The reader thread's IOException is an expected side-effect that is now covered.
        var request = new MavenExecutor.ExecuteRequest(tempDir.toString(), new String[]{"sleep"});
        assertThrows(InterruptedException.class, () -> executor.execute(request));
    }

    @Test
    @DisplayName("Should cover the two-argument constructor and test timeout with a real process")
    void shouldTimeoutWithRealProcess(@TempDir Path tempDir) {
        // Arrange: Instantiate the executor using the constructor that needs to be covered.
        // Use a very short timeout.
        MavenExecutor shortTimeoutExecutor = new MavenExecutor(50, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Act & Assert: Execute a real Maven command that is guaranteed to take longer than the timeout.
        // The `exec-maven-plugin` with a `sleep` goal is perfect for this.
        // We expect an InterruptedException because the main thread's `waitFor` will time out.
        var request = new MavenExecutor.ExecuteRequest(tempDir.toString(), new String[]{"org.codehaus.mojo:exec-maven-plugin:3.2.0:exec", "-Dexec.executable=sleep", "-Dexec.args=1"});
        assertThrows(InterruptedException.class, () -> shortTimeoutExecutor.execute(request));
    }
}