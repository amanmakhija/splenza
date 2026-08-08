package com.splitwise.app.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppConfigIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("app-config is reachable without authentication")
    void isPubliclyAccessible() throws Exception {

        mockMvc.perform(get("/api/v1/app-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestVersion").exists())
                .andExpect(header().exists("Cache-Control"));
    }
}
