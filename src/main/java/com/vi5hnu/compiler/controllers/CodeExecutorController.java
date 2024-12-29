package com.vi5hnu.compiler.controllers;
import com.vi5hnu.compiler.exceptions.ExecutionFailedException;
import com.vi5hnu.compiler.models.CodeExecuteInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class CodeExecutorController {

    @PostMapping("/execute")
    public ResponseEntity<?> executeCode(@RequestBody CodeExecuteInfo codeInfo) {
        try {
            // 1. Write code to a temporary file
            Path tempFile = createTempFile(codeInfo.getLanguage(), codeInfo.getCode());

            // 2. Compile and execute the code
            String result = runCode(codeInfo.getLanguage(), tempFile);

            // 3. Clean up and return the response
            Files.deleteIfExists(tempFile);
            return ResponseEntity.ok(Map.of("result",result));

        }catch (ExecutionFailedException e) {
            return ResponseEntity.ok(Map.of("error",e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    private Path createTempFile(String language, String code) throws IOException {
        String extension = switch (language.toLowerCase()) {
            case "c++", "cpp" -> ".cpp";
            case "java" -> ".java";
            case "python" -> ".py";
            case "javascript", "js" -> ".js";
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };

        String fileName;
        if (language.equalsIgnoreCase("java")) {
            // Extract the class name from the code or use a default name
            fileName = code.contains("class ") ? code.split("class ")[1].split("\\s+")[0] : "Main";
            // Ensure the file name matches the class name for Java
            fileName = fileName + ".java";
            // Create the Java file manually with the exact name
            Path tempFile = Paths.get(System.getProperty("java.io.tmpdir"), fileName);
            Files.writeString(tempFile, code, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return tempFile;
        } else {
            // For other languages, file name can be generic (e.g., "code")
            fileName = "code_" + System.currentTimeMillis();  // Adding a timestamp for uniqueness
            // Create the temp file with a random suffix for other languages
            Path tempFile = Files.createTempFile(fileName, extension);
            Files.writeString(tempFile, code, StandardOpenOption.TRUNCATE_EXISTING);
            return tempFile;
        }
    }




    private String runCode(String language, Path filePath) throws IOException, InterruptedException {
        ProcessBuilder processBuilder;

        // Determine the command based on the language
        switch (language.toLowerCase()) {
            case "c++", "cpp" -> {
                String executable = filePath.toString().replace(".cpp", "");
                processBuilder = new ProcessBuilder("g++", filePath.toString(), "-o", executable);
                executeProcess(processBuilder); // Compile
                processBuilder = new ProcessBuilder(executable); // Run
            }
            case "java" -> {
                processBuilder = new ProcessBuilder("javac", filePath.toString());
                executeProcess(processBuilder); // Compile
                String className = filePath.getFileName().toString().replace(".java", "");
                processBuilder = new ProcessBuilder("java", "-cp", filePath.getParent().toString(), className);
            }
            case "python" -> processBuilder = new ProcessBuilder("python", filePath.toString());
            case "javascript", "js" -> processBuilder = new ProcessBuilder("node", filePath.toString());
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        }

        return executeProcess(processBuilder); // Run the code
    }

    private String executeProcess(ProcessBuilder processBuilder) throws IOException, InterruptedException {
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new ExecutionFailedException(output.toString());
        }
        return output.toString();
    }
}
