package com.vi5hnu.compiler.controllers;
import com.vi5hnu.compiler.exceptions.ExecutionFailedException;
import com.vi5hnu.compiler.models.CodeExecuteInfo;
import com.vi5hnu.compiler.services.CompilerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class CodeExecutorController {
    private final CompilerService compilerService;

    @PostMapping("/execute")
    public ResponseEntity<?> executeCode(@RequestBody CodeExecuteInfo codeInfo) {
        long startTime = System.currentTimeMillis();
        try {
            final var result=compilerService.executeCode(codeInfo.getLanguage(),codeInfo.getCode());
            return ResponseEntity.ok(Map.of("result",result,"executionTime",System.currentTimeMillis()-startTime));
        }catch (ExecutionFailedException e) {
            return ResponseEntity.ok(Map.of("error",e.getMessage(),"executionTime",System.currentTimeMillis()-startTime));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }
}
