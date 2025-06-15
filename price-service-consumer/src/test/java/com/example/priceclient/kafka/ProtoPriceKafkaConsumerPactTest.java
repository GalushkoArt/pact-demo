package com.example.priceclient.kafka;

import au.com.dius.pact.consumer.dsl.PactBuilder;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Interaction;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.example.priceservice.grpc.PriceUpdate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.time.Instant;
import java.util.Map;

import static au.com.dius.pact.consumer.dsl.PactBuilder.filePath;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

//https://github.com/pact-foundation/pact-plugins/blob/main/examples/protobuf/protobuf-consumer-jvm/src/test/java/io/pact/example/protobuf/provider/PactConsumerTest.java
/**
 * Async consumer Pact test verifying protobuf Kafka messages.
 * Uses the Pact protobuf plugin.
 * <p>
 * Тест асинхронного потребителя Pact для сообщений Kafka в формате protobuf.
 * Использует плагин protobuf.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "price-service-provider-kafka-proto", providerType = ProviderType.ASYNCH, pactVersion = PactSpecVersion.V4)
@SpringBootTest(properties = "spring.autoconfigure.exclude=net.devh.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration")
public class ProtoPriceKafkaConsumerPactTest {
    @SpyBean
    private ProtoPriceKafkaConsumer consumer;
    @Captor
    ArgumentCaptor<PriceUpdate> priceUpdateCaptor;
    private static final Instant timestamp = Instant.now();

    /**
     * Defines the contract for a protobuf price update message.
     * <p>
     * Определяет контракт для protobuf-сообщения обновления цены.
     *
     * @see <a href=https://github.com/pact-foundation/pact-plugins/blob/main/docs/matching-rule-definition-expressions.md>link to matchers docs/ссылка на документацию по матчерам</a>
     */
    @Pact(consumer = "price-service-consumer")
    public V4Pact priceUpdatePact(PactBuilder builder) {
        return builder
                .usingPlugin("protobuf")
                .given("price update event")
                .expectsToReceive("proto price updated", "core/interaction/message")
                .addMetadataValue("contentType", "application/protobuf")
                .with(Map.of(
                        "message.contents", Map.of(
                                "pact:proto", filePath("../proto/price_update.proto"),
                                "pact:message-type", "PriceUpdate",
                                "pact:content-type", "application/protobuf",
                                "price", Map.of(
                                        "instrument_id", "matching(type, 'AAPL')",
                                        "bid_price", "matching(number, 175.50)",
                                        "ask_price", "matching(number, 175.75)",
                                        "last_updated", Map.of(
                                                "seconds", format("matching(integer, %d)", timestamp.getEpochSecond()),
                                                "nanos", format("matching(integer, %d)", timestamp.getNano())
                                        )
                                ),
                                "update_type", "UPDATED"
                        )
                ))
                .toPact();
    }

    /**
     * Verifies that the consumer processes the protobuf message correctly.
     * <p>
     * Проверяет корректность обработки protobuf-сообщения потребителем.
     */
    @Test
    @PactTestFor(pactMethod = "priceUpdatePact")
    void testConsumePriceUpdate(V4Interaction.AsynchronousMessage message) {
        consumer.listen(message.contentsAsBytes());
        verify(consumer).processPriceUpdate(priceUpdateCaptor.capture());
        var value = priceUpdateCaptor.getValue();
        com.example.priceservice.grpc.Price price = value.getPrice();
        assertThat(price.getInstrumentId()).isEqualTo("AAPL");
        assertThat(price.getBidPrice()).isEqualTo(175.50);
        assertThat(price.getAskPrice()).isEqualTo(175.75);
        assertThat(price.getLastUpdated().getSeconds()).isEqualTo(timestamp.getEpochSecond());
        assertThat(price.getLastUpdated().getNanos()).isEqualTo(timestamp.getNano());
    }
}
