package com.example.minagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ToolSchemaValidator {

    public Optional<String> validate(ToolDefinition definition, JsonNode arguments) {
        ObjectNode schema = definition.parametersSchema();
        if (schema == null) {
            return Optional.empty(); // no schema = no validation
        }

        // Check additionalProperties
        boolean allowAdditional = schema.path("additionalProperties").asBoolean(true);
        Set<String> definedProps = new HashSet<>();
        JsonNode properties = schema.get("properties");
        if (properties != null) {
            properties.fieldNames().forEachRemaining(definedProps::add);
        }
        if (!allowAdditional && arguments.isObject()) {
            Iterator<String> argFields = arguments.fieldNames();
            while (argFields.hasNext()) {
                String field = argFields.next();
                if (!definedProps.contains(field)) {
                    return Optional.of("Unknown field: " + field);
                }
            }
        }

        // Check required fields
        JsonNode required = schema.get("required");
        if (required != null && required.isArray()) {
            for (JsonNode req : required) {
                String fieldName = req.asText();
                if (!arguments.has(fieldName) || arguments.get(fieldName).isNull()) {
                    return Optional.of("Missing required field: " + fieldName);
                }
            }
        }

        // Check field types
        for (String fieldName : definedProps) {
            if (!arguments.has(fieldName)) continue;
            JsonNode propSchema = properties.get(fieldName);
            String expectedType = propSchema.path("type").asText();
            JsonNode value = arguments.get(fieldName);

            if (!expectedType.isEmpty() && !matchesType(expectedType, value)) {
                return Optional.of("Field " + fieldName + " should be " + expectedType);
            }

            // Check string constraints
            if ("string".equals(expectedType)) {
                int minLen = propSchema.path("minLength").asInt(-1);
                int maxLen = propSchema.path("maxLength").asInt(-1);
                if (minLen > 0 && value.asText().length() < minLen) {
                    return Optional.of("Field " + fieldName + " is too short (min " + minLen + ")");
                }
                if (maxLen > 0 && value.asText().length() > maxLen) {
                    return Optional.of("Field " + fieldName + " is too long (max " + maxLen + ")");
                }
            }

            // Check number constraints
            if ("integer".equals(expectedType) || "number".equals(expectedType)) {
                if (propSchema.has("minimum") && value.asDouble() < propSchema.get("minimum").asDouble()) {
                    return Optional.of("Field " + fieldName + " is below minimum");
                }
                if (propSchema.has("maximum") && value.asDouble() > propSchema.get("maximum").asDouble()) {
                    return Optional.of("Field " + fieldName + " is above maximum");
                }
            }

            // Check enum
            JsonNode enumValues = propSchema.get("enum");
            if (enumValues != null && enumValues.isArray()) {
                boolean match = false;
                for (JsonNode ev : enumValues) {
                    if (ev.asText().equals(value.asText())) {
                        match = true;
                        break;
                    }
                }
                if (!match) {
                    return Optional.of("Field " + fieldName + " must be one of: " + enumValues);
                }
            }
        }

        return Optional.empty();
    }

    private boolean matchesType(String expectedType, JsonNode value) {
        return switch (expectedType) {
            case "string" -> value.isTextual();
            case "integer" -> value.isInt() || value.isLong();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            default -> true;
        };
    }
}
