package com.example.minagent.tool.impl;

import com.example.minagent.tool.AgentTool;
import com.example.minagent.tool.ToolContext;
import com.example.minagent.tool.ToolDefinition;
import com.example.minagent.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WeatherTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, WeatherData> WEATHER_MAP = Map.of(
            "北京", new WeatherData("晴", "28°C", "35%"),
            "上海", new WeatherData("多云", "30°C", "68%"),
            "杭州", new WeatherData("小雨", "27°C", "76%"),
            "深圳", new WeatherData("雷阵雨", "31°C", "81%")
    );

    @Override
    public ToolDefinition definition() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode props = MAPPER.createObjectNode();
        ObjectNode cityProp = MAPPER.createObjectNode();
        cityProp.put("type", "string");
        cityProp.put("description", "中文城市名，例如北京、上海、杭州");
        cityProp.put("minLength", 1);
        cityProp.put("maxLength", 40);
        props.set("city", cityProp);

        schema.set("properties", props);
        var required = schema.putArray("required");
        required.add("city");
        schema.put("additionalProperties", false);

        return ToolDefinition.of("weather",
                "查询指定城市的模拟当前天气。天气相关问题必须使用此工具，不可根据模型知识猜测。",
                schema);
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        String city = arguments.path("city").asText();

        WeatherData data = WEATHER_MAP.get(city);
        if (data == null) {
            return ToolResult.failure("CITY_NOT_FOUND",
                    "未知城市: " + city + "。当前可查询的城市: " + WEATHER_MAP.keySet());
        }

        ObjectNode resultData = MAPPER.createObjectNode();
        resultData.put("city", city);
        resultData.put("weather", data.weather);
        resultData.put("temperature", data.temperature);
        resultData.put("humidity", data.humidity);

        String message = String.format("%s当前天气: %s, 温度 %s, 湿度 %s。",
                city, data.weather, data.temperature, data.humidity);

        return ToolResult.success(resultData, message);
    }

    record WeatherData(String weather, String temperature, String humidity) {
    }
}
