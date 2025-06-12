package com.example.priceclient.kafka;

import com.example.priceservice.grpc.PriceUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that processes protobuf price update events.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProtoPriceKafkaConsumer {

    @KafkaListener(topics = "${price.kafka.proto.topic:price-updates.proto}", groupId = "price-client-proto", containerFactory = "protoKafkaListenerContainerFactory")
    public void listen(byte[] message) {
        try {
            PriceUpdate event = PriceUpdate.parseFrom(message);
            processPriceUpdate(event);
        } catch (Exception e) {
            log.error("Failed to process protobuf price update", e);
        }
    }

    void processPriceUpdate(PriceUpdate message) {
        log.info("Received protobuf price update: {}", message);
        // further processing logic would go here
    }
}
