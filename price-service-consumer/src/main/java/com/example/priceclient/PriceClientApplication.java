package com.example.priceclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Price Service Consumer.
 * This client consumes the price service API.
 */
@SpringBootApplication
public class PriceClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(PriceClientApplication.class, args);
    }
}
