package com.example.priceclient.config;

import com.example.priceservice.client.ApiClient;
import com.example.priceservice.client.api.OrderBookApi;
import com.example.priceservice.client.api.PricesApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configuration for REST client components.
 */
@Configuration
public class RestClientConfig {

    @Bean
    ApiClient apiClient(
            @Value("${price-service.base-url}") String baseUrl,
            @Value("${price-service.username:admin}") String username,
            @Value("${price-service.password:password}") String password,
            RestClient.Builder builder
    ) {
        var apiClient = new ApiClient(builder.build());
        apiClient.setUsername(username);
        apiClient.setPassword(password);
        apiClient.setBasePath(baseUrl);
        return apiClient;
    }

    @Bean
    PricesApi pricesApi(ApiClient apiClient) {
        return new PricesApi(apiClient);
    }

    @Bean
    OrderBookApi orderBookApi(ApiClient apiClient) {
        return new OrderBookApi(apiClient);
    }
}
