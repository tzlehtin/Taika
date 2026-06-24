# API Documentation: taika-tools

This document describes the public service methods exposed by the `dev.taika:taika-tools` module.

## Component: MavenExecutor
Executes Maven commands in a specified directory and captures the full console output.
* **Method:** `public String execute(Path projectPath, String... goals)`
  * **Input:** The target project directory (`projectPath`) and a sequence of Maven goals (`goals`), e.g., `"clean"`, `"install"`.
  * **Output:** A single `String` containing the combined stdout and stderr from the Maven process, mimicking terminal output.

## Component: FileSystemReader
Recursively finds and reads files based on glob patterns.
* **Method:** `public Map<Path, String> readAll(Path rootPath, String... globPatterns)`
  * **Input:** The directory to scan from (`rootPath`) and one or more glob patterns (`globPatterns`), e.g., `"*.java"`, `"context.md"`.
  * **Output:** A `Map<Path, String>` where each key is the absolute path to a matched file and the value is its full content as a string.

## Component: FileSystemWriter
Writes or overwrites content to a file, creating parent directories if they don't exist.
* **Method:** `public void write(Path filePath, String content)`
  * **Input:** The absolute path of the file to be written (`filePath`) and the `String` content to write.
  * **Output:** `void`.