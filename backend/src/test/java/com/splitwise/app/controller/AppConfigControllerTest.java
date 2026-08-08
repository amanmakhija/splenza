package com.splitwise.app.controller;

import com.splitwise.app.config.AppVersionProperties;
import com.splitwise.app.exception.GlobalExceptionHandler;
import com.splitwise.app.ratelimit.RateLimitFilter;
import com.splitwise.app.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

import com.splitwise.app.config.SecurityConfig;

@WebMvcTest(
        controllers = AppConfigController.class,
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
            @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
            @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class)
        }
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AppConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppVersionProperties appVersionProperties;

    @Test
    @DisplayName("Returns latestVersion and releaseNotes with a cache-control header")
    void shouldReturnAppConfig() throws Exception {

        when(appVersionProperties.getLatestVersion()).thenReturn("1.2.0");
        when(appVersionProperties.getReleaseNotes())
                .thenReturn("Faster expense splitting and a few bug fixes.");

        mockMvc.perform(get("/api/v1/app-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestVersion").value("1.2.0"))
                .andExpect(jsonPath("$.releaseNotes").value("Faster expense splitting and a few bug fixes."))
                .andExpect(header().exists("Cache-Control"));
    }

    @Test
    @DisplayName("Returns null releaseNotes when unset rather than omitting or blank-stringing it")
    void shouldReturnNullReleaseNotesWhenUnset() throws Exception {

        when(appVersionProperties.getLatestVersion()).thenReturn("1.2.0");
        when(appVersionProperties.getReleaseNotes()).thenReturn(null);

        mockMvc.perform(get("/api/v1/app-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestVersion").value("1.2.0"))
                .andExpect(jsonPath("$.releaseNotes").value(nullValue()));
    }

    @Test
    @DisplayName("Blank release notes are also normalized to null")
    void shouldTreatBlankReleaseNotesAsNull() throws Exception {

        when(appVersionProperties.getLatestVersion()).thenReturn("1.2.0");
        when(appVersionProperties.getReleaseNotes()).thenReturn("   ");

        mockMvc.perform(get("/api/v1/app-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releaseNotes").value(nullValue()));
    }
}
