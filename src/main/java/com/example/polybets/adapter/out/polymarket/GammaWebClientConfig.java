package com.example.polybets.adapter.out.polymarket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class GammaWebClientConfig {

    @Bean
    public WebClient gammaWebClient(@Value("${polymarket.gamma-api-base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", "polymarket-consensus-platform/0.1 (+spring-boot)")
                .build();
    }
}
