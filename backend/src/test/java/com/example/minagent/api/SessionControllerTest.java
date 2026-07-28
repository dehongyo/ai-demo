package com.example.minagent.api;

import com.example.minagent.session.*;
import com.example.minagent.session.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private ChatSessionRepository sessionRepository;
    @Autowired
    private ChatMessageRepository messageRepository;

    private static final UUID USER_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        sessionRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    @Test
    void createsSession() throws Exception {
        mockMvc.perform(post("/api/sessions")
                        .header("X-User-Id", USER_A)
                        .contentType("application/json")
                        .content("{\"title\":\"Weather & Travel\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Weather & Travel"))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void listsSessions() throws Exception {
        // Create a session first
        mockMvc.perform(post("/api/sessions")
                .header("X-User-Id", USER_A)
                .contentType("application/json")
                .content("{\"title\":\"Session One\"}"));

        mockMvc.perform(get("/api/sessions")
                        .header("X-User-Id", USER_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Session One"));
    }

    @Test
    void userBCannotReadUserASession() throws Exception {
        String result = mockMvc.perform(post("/api/sessions")
                        .header("X-User-Id", USER_A)
                        .contentType("application/json")
                        .content("{\"title\":\"A's Session\"}"))
                .andReturn().getResponse().getContentAsString();

        ObjectMapper mapper = new ObjectMapper();
        UUID id = UUID.fromString(mapper.readTree(result).get("id").asText());

        mockMvc.perform(get("/api/sessions/{id}", id)
                        .header("X-User-Id", USER_B))
                .andExpect(status().isNotFound());
    }

    @Test
    void createsSessionWithoutTitleReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/sessions")
                        .header("X-User-Id", USER_A)
                        .contentType("application/json")
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
