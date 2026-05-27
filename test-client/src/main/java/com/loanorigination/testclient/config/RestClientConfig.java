package com.loanorigination.testclient.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient gatewayRestClient(@Value("${gateway.base-url}") String gatewayBaseUrl) {
        return RestClient.builder()
                .baseUrl(gatewayBaseUrl)
                .build();
    }
}
