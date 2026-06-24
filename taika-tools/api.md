# API Documentation: taika-tools

This document describes the public service methods exposed by the `dev.taika:taika-tools` module.

## Component: MavenExecutor
Executes Maven commands in a specified directory and captures the full console output.
* **Method:** `public String execute(MavenExecutor.ExecuteRequest request)`
  * **Input:** An `ExecuteRequest` record containing `projectPath` (String) and `goals` (String[]).
  * **Output:** A single `String` containing the combined stdout and stderr from the Maven process, mimicking terminal output.

## Component: FileSystemReader
Recursively finds and reads files based on glob patterns.
* **Method:** `public Map<String, String> readAll(FileSystemReader.ReadRequest request)`
  * **Input:** A `ReadRequest` record containing `rootPath` (String) and `globPatterns` (String[]).
  * **Output:** A `Map<String, String>` where each key is the absolute path to a matched file and the value is its full content as a string.

## Component: FileSystemWriter
Writes or overwrites content to a file, creating parent directories if they don't exist.
* **Method:** `public String write(FileSystemWriter.WriteRequest request)`
  * **Input:** A `WriteRequest` record containing `filePath` (String) and `content` (String).
  * **Output:** A confirmation `String` (e.g., "Successfully wrote to /path/to/file.java").

## Component: FileReader
Reads the content of a single file from a given path.
* **Method:** `public String readFile(FileReader.ReadFileRequest request)`
  * **Input:** A `ReadFileRequest` record containing `filePath` (String).
  * **Output:** The file's content as a `String`.