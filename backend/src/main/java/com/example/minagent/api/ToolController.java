package com.example.minagent.api;

import com.example.minagent.api.dto.ToolResponse;
import com.example.minagent.tool.ToolRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private final ToolRegistry toolRegistry;

    public ToolController(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @GetMapping
    public List<ToolResponse> listTools() {
        return toolRegistry.toolDefinitions().stream()
                .map(ToolResponse::from)
                .toList();
    }
}
