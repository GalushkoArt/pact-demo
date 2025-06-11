package com.example.priceclient.kafka;

import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.dsl.PactBuilder;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.messaging.Message;
import com.example.priceservice.grpc.PriceUpdate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.util.List;
import java.util.Map;

import static au.com.dius.pact.consumer.dsl.PactBuilder.filePath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "price-service-provider-kafka-proto", providerType = ProviderType.ASYNCH, pactVersion = PactSpecVersion.V4)
@SpringBootTest(properties = "spring.autoconfigure.exclude=net.devh.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration")
public class ProtoPriceKafkaConsumerPactTest {
    @SpyBean
    private ProtoPriceKafkaConsumer consumer;
    @Captor
    ArgumentCaptor<PriceUpdate> priceUpdateCaptor;

    @Pact(consumer = "new-price-service-consumer")
    public V4Pact priceUpdatePact(PactBuilder builder) {
        return builder
                .usingPlugin("protobuf")
                .given("price update event")
                .expectsToReceive("price updated", "core/interaction/message")
                .with(Map.of(
                        "message.contents", Map.of(
                                "pact:proto", filePath("../proto/price_update.proto"),
                                "pact:message-type", "PriceUpdate",
                                "pact:content-type", "application/protobuf",
                                "price", Map.of(
                                        "instrument_id", "AAPL",
                                        "bid_price", "matching(number, 175.50)",
                                        "ask_price", "matching(number, 175.75)"
                                ),
                                "update_type", "UPDATED"
                        )
                ))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "priceUpdatePact")
    void testConsumePriceUpdate(List<Message> messages) throws Exception {
        consumer.listen(messages.getFirst().contentsAsBytes());
        verify(consumer).processPriceUpdate(priceUpdateCaptor.capture());
        var value = priceUpdateCaptor.getValue();
        com.example.priceservice.grpc.Price price = value.getPrice();
        assertThat(price.getInstrumentId()).isEqualTo("AAPL");
        assertThat(price.getBidPrice()).isEqualTo(175.50);
        assertThat(price.getAskPrice()).isEqualTo(175.75);
    }
}
