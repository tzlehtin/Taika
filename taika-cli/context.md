# Module Context: taika-cli

## Responsibility
The native Linux terminal dashboard and human-in-the-loop interaction layer. 

## Critical Dependency Setup
Uses a locked, stable version of Spring Shell Starter declared directly in the module dependencies:

```xml
<dependency>
    <groupId>org.springframework.shell</groupId>
    <artifactId>spring-shell-starter</artifactId>
    <version>3.3.1</version>
</dependency>