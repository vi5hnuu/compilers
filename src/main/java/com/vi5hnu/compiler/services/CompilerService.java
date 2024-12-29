package com.vi5hnu.compiler.services;

import com.vi5hnu.compiler.exceptions.ExecutionFailedException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class CompilerService {
    private boolean isValidLanguage(String language) {
        return List.of("c++", "cpp", "java", "python", "javascript", "js").contains(language.toLowerCase());
    }

    private boolean isValidCode(String code) {
        return code != null && code.length() < 10_000 && !code.contains("rm -rf") && !code.contains("System.exit");
    }

    public String executeCode(String language,String code) throws IOException, InterruptedException {
        if (!isValidLanguage(language)) {
            throw new IllegalArgumentException("Unsupported or invalid language.");
        }
        if (!isValidCode(code)) {
            throw new IllegalArgumentException("Code contains invalid or unsafe content.");
        }
        // 1. Write code to a temporary file
        Path tempFile = createTempFile(language, code);

        // 2. Compile and execute the code
        try {
            return runCode(language, tempFile);
        }catch (Exception e){
            throw e;
        }finally {
            // 3. Clean up and return the response
            Files.deleteIfExists(tempFile);
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
        processBuilder.redirectErrorStream(true); // Redirect error stream to the output stream
        Process process = processBuilder.start(); // Start the process

        // Use a separate thread to read the process output asynchronously
        StringBuilder output = new StringBuilder();
        final Thread outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null) {
                    if (lineCount >= 1000) {
                        output.append("[Output truncated after 1000 lines]\n");
                        break;
                    }
                    output.append(line).append("\n");
                    lineCount++;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        outputThread.start(); // Start the output reading thread

        // Wait for the process to complete with a timeout
        boolean finished = process.waitFor(5, TimeUnit.SECONDS); // Wait for the process to complete within 5 seconds

        if (!finished) {
            process.destroy(); // Destroy the process if it exceeds the timeout
            outputThread.interrupt(); // Interrupt the output reading thread
            throw new ExecutionFailedException("Time limit exceeded");
        }
        outputThread.join(); // Ensure the output thread finishes before continuing

        int exitCode = process.exitValue(); // Get the exit code of the process
        if (exitCode != 0) {
            throw new ExecutionFailedException(output.toString()); // If the process failed, throw an exception with the output
        }

        return output.toString(); // Return the captured output
    }

}
