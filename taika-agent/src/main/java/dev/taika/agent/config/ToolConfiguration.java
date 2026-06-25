package dev.taika.agent.config;

import dev.taika.tools.service.FileReader;
import dev.taika.tools.service.FileSystemReader;
import dev.taika.tools.service.FileSystemWriter;
import dev.taika.tools.service.MavenExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class ToolConfiguration {

    @Bean
    @Description("Recursively finds and reads files matching glob patterns from a root directory.")
    public Function<FileSystemReader.ReadRequest, java.util.Map<String, String>> fileSystemReaderFunction(FileSystemReader service) {
        return request -> {
            try {
                return service.readAll(request);
            } catch (Exception e) {
                throw new RuntimeException("Error executing fileSystemReader", e);
            }
        };
    }

    @Bean
    @Description("Writes or overwrites content to a specified file path.")
    public Function<FileSystemWriter.WriteRequest, String> fileSystemWriterFunction(FileSystemWriter service) {
        return request -> {
            try {
                return service.write(request);
            } catch (Exception e) {
                throw new RuntimeException("Error executing fileSystemWriter", e);
            }
        };
    }

    @Bean
    @Description("Executes Maven commands (e.g., 'clean install') in a specified project directory.")
    public Function<MavenExecutor.ExecuteRequest, String> mavenExecutorFunction(MavenExecutor service) {
        return request -> {
            try {
                return service.execute(request);
            } catch (Exception e) {
                throw new RuntimeException("Error executing mavenExecutor", e);
            }
        };
    }

    @Bean
    @Description("Reads the content of a single file from a given path.")
    public Function<FileReader.ReadFileRequest, String> fileReaderFunction(FileReader service) {
        return request -> {
            try {
                return service.readFile(request);
            } catch (Exception e) {
                throw new RuntimeException("Error executing fileReader", e);
            }
        };
    }
}