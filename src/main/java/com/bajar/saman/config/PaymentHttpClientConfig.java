package com.bajar.saman.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PaymentHttpClientConfig {

    @Bean
    RestClient.Builder paymentRestClientBuilder() {
        return RestClient.builder();
    }
}
