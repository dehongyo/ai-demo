package com.example.minagent.tool.impl;

import com.example.minagent.tool.AgentTool;
import com.example.minagent.tool.ToolContext;
import com.example.minagent.tool.ToolDefinition;
import com.example.minagent.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CalculatorTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ToolDefinition definition() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode props = MAPPER.createObjectNode();
        ObjectNode exprProp = MAPPER.createObjectNode();
        exprProp.put("type", "string");
        exprProp.put("description", "算术表达式，例如 (12.5+7.5)*3");
        exprProp.put("minLength", 1);
        exprProp.put("maxLength", 200);
        props.set("expression", exprProp);
        schema.set("properties", props);

        var required = schema.putArray("required");
        required.add("expression");
        schema.put("additionalProperties", false);

        return ToolDefinition.of("calculator",
                "对只包含数字、括号和 + - * / 的表达式进行精确计算。需要准确算术时使用。", schema);
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        String expression = arguments.path("expression").asText();

        try {
            ExpressionParser parser = new ExpressionParser(expression);
            BigDecimal result = parser.parse();
            String resultStr = result.stripTrailingZeros().toPlainString();

            ObjectNode data = MAPPER.createObjectNode();
            data.put("expression", expression);
            data.put("result", resultStr);

            return ToolResult.success(data,
                    "计算结果: " + expression + " = " + resultStr);
        } catch (ArithmeticException e) {
            String code = e.getMessage();
            if ("DIVISION_BY_ZERO".equals(code)) {
                return ToolResult.failure("DIVISION_BY_ZERO",
                        "除数不能为零，请检查表达式: " + expression);
            }
            return ToolResult.failure("INVALID_EXPRESSION",
                    "无法计算表达式 \"" + expression + "\": " + e.getMessage());
        }
    }
}
