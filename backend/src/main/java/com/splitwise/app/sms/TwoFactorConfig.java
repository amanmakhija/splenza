package com.splitwise.app.sms;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.sms", name = "provider", havingValue = "twofactor")
public class TwoFactorConfig {

    private final TwoFactorProperties twoFactorProperties;

    @Bean
    public RestClient twoFactorRestClient() {
        return RestClient.builder()
                .baseUrl(twoFactorProperties.getBaseUrl())
                .build();
    }
}
