package com.example.priceclient.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that processes price update events.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PriceKafkaConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${price.kafka.topic:price-updates}", groupId = "price-client")
    public void listen(String message) {
        try {
            PriceUpdateMessage event = objectMapper.readValue(message, PriceUpdateMessage.class);
            processPriceUpdate(event);
        } catch (Exception e) {
            log.error("Failed to process price update", e);
        }
    }

    void processPriceUpdate(PriceUpdateMessage message) {
        log.info("Received price update: {}", message);
        // further processing logic would go here
    }
}
