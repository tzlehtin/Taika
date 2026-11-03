package dev.taika.agent.tools;

import dev.taika.api.AgentType;
import dev.taika.tools.service.FileReader;
import dev.taika.tools.service.FileSystemReader;
import dev.taika.tools.service.FileSystemWriter;
import dev.taika.tools.service.MavenExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

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
        assertEquals(3, toolRegistry.getToolCallbacks().size(), "Should register 3 tools.");
    }

    @Test
    void shouldRegisterToolsWithCorrectNames() {
        Set<String> toolNames = toolRegistry.getToolCallbacks().stream()
                .map(cb -> cb.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertEquals(3, toolNames.size());
        assertTrue(toolNames.contains("fileSystemReader"));
        assertTrue(toolNames.contains("fileSystemWriter"));
        assertTrue(toolNames.contains("fileReader"));
    }

    @Test
    void shouldRegisterOnlyWriterForRobotTester() {
        var registry = new ToolRegistry(fileSystemReader, fileSystemWriter, mavenExecutor, fileReader, Path.of("."), AgentType.ROBOT_TESTER);
        assertEquals(1, registry.getToolCallbacks().size());
        assertEquals("fileSystemWriter", registry.getToolCallbacks().get(0).getToolDefinition().name());
    }

    @Test
    void shouldRegisterNoToolsForValidator() {
        var registry = new ToolRegistry(fileSystemReader, fileSystemWriter, mavenExecutor, fileReader, Path.of("."), AgentType.VALIDATOR);
        assertTrue(registry.getToolCallbacks().isEmpty());
    }
}