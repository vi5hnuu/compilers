package com.vi5hnu.compiler.models;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
public class CodeExecuteInfo {
    private String language;
    private String code;

    public String getLanguage() {
        return language;
    }

    public String getCode() {
        return code;
    }
}
