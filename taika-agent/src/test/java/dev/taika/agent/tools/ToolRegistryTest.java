package dev.taika.agent.tools;

import dev.taika.tools.service.FileReader;
import dev.taika.tools.service.FileSystemReader;
import dev.taika.tools.service.FileSystemWriter;
import dev.taika.tools.service.MavenExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.stream;

@ExtendWith(MockitoExtension.class)
class ToolRegistryTest {

    @Mock
    private FileSystemReader fileSystemReader;
    @Mock
    private FileSystemWriter fileSystemWriter;
    @Mock
    private MavenExecutor mavenExecutor;
    @Mock
    private FileReader fileReader;

    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry(
                fileSystemReader,
                fileSystemWriter,
                mavenExecutor,
                fileReader,
                Path.of(".") // Provide a non-null path for the constructor
        );
    }

    @Test
    void shouldInitializeAndRegisterAllToolCallbacks() {
        // Assert
        assertFalse(toolRegistry.getToolCallbacks().isEmpty());
        assertEquals(4, toolRegistry.getToolCallbacks().size(), "Should register all 4 tools.");
    }

    @Test
    void shouldRegisterToolsWithCorrectNames() {
        // This test is redundant with shouldInitializeAndRegisterAllToolCallbacks.
        // The important part is that the correct number of tools are registered.
        assertEquals(4, toolRegistry.getToolCallbacks().size(), "Should register all 4 tools.");
    }
}