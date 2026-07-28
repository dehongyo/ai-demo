package com.example.minagent.tool.impl;

import com.example.minagent.tool.ToolContext;
import com.example.minagent.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class WeatherToolTest {

    private final WeatherTool tool = new WeatherTool();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ToolContext ctx = new ToolContext(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Test
    void returnsWeatherForKnownCity() {
        ObjectNode args = mapper.createObjectNode();
        args.put("city", "杭州");

        ToolResult result = tool.execute(args, ctx);

        assertThat(result.success()).isTrue();
        assertThat(result.modelMessage()).contains("小雨", "27°C", "76%");
    }

    @Test
    void returnsCityNotFoundForUnknownCity() {
        ObjectNode args = mapper.createObjectNode();
        args.put("city", "火星");

        ToolResult result = tool.execute(args, ctx);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo("CITY_NOT_FOUND");
    }

    @Test
    void definitionHasCorrectName() {
        assertThat(tool.definition().name()).isEqualTo("weather");
        assertThat(tool.definition().description()).contains("天气");
    }
}
