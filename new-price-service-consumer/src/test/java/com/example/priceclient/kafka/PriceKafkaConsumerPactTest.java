package com.example.priceclient.kafka;

import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.messaging.Message;
import au.com.dius.pact.core.model.messaging.MessagePact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "price-service-provider-kafka", providerType = ProviderType.ASYNCH, pactVersion = PactSpecVersion.V3)
@SpringBootTest(properties = "spring.autoconfigure.exclude=net.devh.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration")
public class PriceKafkaConsumerPactTest {
    @SpyBean
    private PriceKafkaConsumer consumer;
    @Captor
    ArgumentCaptor<PriceUpdateMessage> priceUpdateCaptor;

    /**
     * Defines the contract for a single price update message.
     * Using Pact DSL keeps the schema explicit and versioned alongside the test.
     * <p>
     * Определяет контракт для одного сообщения обновления цены.
     * Использование Pact DSL делает схему явной и версионируемой вместе с тестом.
     */
    @Pact(consumer = "new-price-service-consumer")
    public MessagePact priceUpdatePact(MessagePactBuilder builder) {
        return builder
                .given("price update event")
                .expectsToReceive("price updated")
                .withMetadata(Map.of("contentType", "application/json"))
                .withContent(newJsonBody(body -> {
                    body.stringType("instrumentId", "AAPL");
                    body.numberType("bidPrice", 175.50);
                    body.numberType("askPrice", 175.75);
                    body.stringMatcher("lastUpdated", ".+", "2024-01-01T00:00:00Z");
                }).build())
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "priceUpdatePact")
    void testConsumePriceUpdate(List<Message> messages) {
        consumer.listen(messages.getFirst().contentsAsString());
        verify(consumer).processPriceUpdate(priceUpdateCaptor.capture());
        var value = priceUpdateCaptor.getValue();
        assertThat(value.getInstrumentId()).isEqualTo("AAPL");
        assertThat(value.getBidPrice()).isEqualTo(BigDecimal.valueOf(175.50));
        assertThat(value.getAskPrice()).isEqualTo(BigDecimal.valueOf(175.75));
        assertThat(value.getLastUpdated()).isEqualTo("2024-01-01T00:00:00Z");
    }
}
