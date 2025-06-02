package com.example.priceservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Price Service Provider.
 * This service provides price information for financial instruments.
 */
@SpringBootApplication
public class PriceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PriceServiceApplication.class, args);
    }
}
