package com.example.priceclient.kafka;

import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.core.model.messaging.MessagePact;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.messaging.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "price-service-provider-kafka", providerType = ProviderType.ASYNCH, pactVersion = PactSpecVersion.V3)
public class PriceKafkaConsumerPactTest {

    /**
     * Defines the contract for a single price update message.
     * Using Pact DSL keeps the schema explicit and versioned alongside the test.
     * <p>
     * Определяет контракт для одного сообщения обновления цены.
     * Использование Pact DSL делает схему явной и версионируемой вместе с тестом.
     */
    @Pact(consumer = "new-price-service-consumer")
    public MessagePact priceUpdatePact(MessagePactBuilder builder) {
        PactDslJsonBody body = new PactDslJsonBody()
                .stringType("instrumentId", "AAPL")
                .decimalType("bidPrice", 175.50)
                .decimalType("askPrice", 175.75)
                .stringMatcher("lastUpdated", ".+", "2024-01-01T00:00:00Z");
        return builder
                .given("price update event")
                .expectsToReceive("price updated")
                .withMetadata(Map.of("contentType", "application/json"))
                .withContent(body)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "priceUpdatePact")
    void testConsumePriceUpdate(List<Message> messages) throws Exception {
        // Deserialize the Pact-generated message and pass it to the consumer
        // Десериализуем сообщение, созданное Pact, и передаем его потребителю
        String json = messages.get(0).getContents().valueAsString();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        PriceKafkaConsumer consumer = new PriceKafkaConsumer(mapper);

        // Best practice is to test the consumer's processing logic, not just JSON parsing
        // Лучшая практика — тестировать бизнес-логику потребителя, а не только разбор JSON
        consumer.processPriceUpdate(mapper.readValue(json, PriceUpdateMessage.class));
        assertThat(json).isNotBlank();
    }
}
