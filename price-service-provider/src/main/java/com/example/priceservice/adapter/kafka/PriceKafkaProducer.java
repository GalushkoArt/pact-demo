package com.example.priceservice.adapter.kafka;

import com.example.priceservice.domain.model.Price;
import com.example.priceservice.domain.model.PriceUpdateMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka producer for publishing price update events.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PriceKafkaProducer {

    private final KafkaTemplate<String, PriceUpdateMessage> kafkaTemplate;

    @Value("${price.kafka.topic:price-updates}")
    private String topic;

    public void sendPriceUpdate(Price price) {
        PriceUpdateMessage message = PriceUpdateMessage.fromDomain(price);
        kafkaTemplate.send(topic, message.getInstrumentId(), message);
        log.info("Sent price update to Kafka: {}", message);
    }
}
