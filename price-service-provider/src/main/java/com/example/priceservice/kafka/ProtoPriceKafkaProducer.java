package com.example.priceservice.kafka;

import com.example.priceservice.grpc.PriceUpdate;
import com.example.priceservice.grpc.UpdateType;
import com.google.protobuf.Timestamp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka producer that publishes price updates encoded with Protocol Buffers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProtoPriceKafkaProducer {

    private final KafkaTemplate<String, byte[]> protoKafkaTemplate;

    @Value("${price.kafka.proto.topic:price-updates-proto.proto}")
    private String topic;

    public void sendPriceUpdate(com.example.priceservice.domain.model.Price price) {
        com.example.priceservice.grpc.Price priceMsg = com.example.priceservice.grpc.Price.newBuilder()
                .setInstrumentId(price.getInstrumentId())
                .setBidPrice(price.getBidPrice().doubleValue())
                .setAskPrice(price.getAskPrice().doubleValue())
                .setLastUpdated(Timestamp.newBuilder()
                        .setSeconds(price.getLastUpdated().getEpochSecond())
                        .setNanos(price.getLastUpdated().getNano())
                        .build())
                .build();
        PriceUpdate message = PriceUpdate.newBuilder()
                .setPrice(priceMsg)
                .setUpdateType(UpdateType.UPDATED)
                .build();
        protoKafkaTemplate.send(topic, price.getInstrumentId(), message.toByteArray());
        log.info("Sent protobuf price update to Kafka: {}", message);
    }
}
