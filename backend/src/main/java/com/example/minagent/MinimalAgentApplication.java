package com.example.minagent;

import com.example.minagent.config.AgentProperties;
import com.example.minagent.config.BailianProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({BailianProperties.class, AgentProperties.class})
public class MinimalAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MinimalAgentApplication.class, args);
    }
}
