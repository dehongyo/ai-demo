package com.example.minagent.tool;

public class UnknownToolException extends RuntimeException {

    public UnknownToolException(String toolName) {
        super("Unknown tool: " + toolName);
    }
}
