package com.example.minagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.agent")
public class AgentProperties {

    private int maxSteps = 8;
    private int recentMessageCount = 12;
    private int compressMessageThreshold = 30;
    private int contextTokenBudget = 12000;

    public int getMaxSteps() { return maxSteps; }
    public void setMaxSteps(int maxSteps) { this.maxSteps = maxSteps; }

    public int getRecentMessageCount() { return recentMessageCount; }
    public void setRecentMessageCount(int recentMessageCount) { this.recentMessageCount = recentMessageCount; }

    public int getCompressMessageThreshold() { return compressMessageThreshold; }
    public void setCompressMessageThreshold(int compressMessageThreshold) { this.compressMessageThreshold = compressMessageThreshold; }

    public int getContextTokenBudget() { return contextTokenBudget; }
    public void setContextTokenBudget(int contextTokenBudget) { this.contextTokenBudget = contextTokenBudget; }
}
