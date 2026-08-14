package com.splitwise.app.config;

import com.splitwise.app.ratelimit.RateLimitFilter;
import com.splitwise.app.security.AdminBroadcastFilter;
import com.splitwise.app.security.RtdnWebhookFilter;
import com.splitwise.app.security.JwtAuthenticationEntryPoint;
import com.splitwise.app.security.JwtAuthenticationFilter;
import com.splitwise.app.logging.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final RateLimitFilter rateLimitFilter;
    private final CorsProperties corsProperties;
    private final RequestIdFilter requestIdFilter;
    private final AdminBroadcastFilter adminBroadcastFilter;
    private final RtdnWebhookFilter rtdnWebhookFilter;

    private static final String[] PUBLIC_ENDPOINTS = {
        "/api/v1/auth/**",
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/api/v1/waitlist/**",
        "/api/v1/app-config",
        "/actuator/health",
        // Not actually "public" - gated entirely by AdminBroadcastFilter's own
        // secret check below, deliberately outside the JWT/user auth system.
        "/api/v1/admin/notifications/broadcast",
        // Not actually "public" either - gated by RtdnWebhookFilter's own
        // shared-secret check below. Called directly by Google Cloud Pub/Sub,
        // which has no JWT to present.
        "/api/v1/ai-credits/rtdn-webhook"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        log.info("Configuring Spring Security filter chain.");

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .anonymous(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/api/v1/auth/logout",
                        "/api/v1/auth/change-password",
                        "/api/v1/auth/account",
                        "/api/v1/auth/identifiers/**"
                ).authenticated()
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .anyRequest().authenticated()
                )
                .addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(adminBroadcastFilter, RequestIdFilter.class)
                .addFilterBefore(rtdnWebhookFilter, RequestIdFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, RequestIdFilter.class)
                .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);

        log.info("Spring Security configured successfully.");

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        log.info("Configuring CORS.");

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(corsProperties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
