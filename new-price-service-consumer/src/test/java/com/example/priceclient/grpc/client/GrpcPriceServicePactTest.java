package com.example.priceclient.grpc.client;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactBuilder;
import au.com.dius.pact.consumer.junit.MockServerConfig;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.consumer.model.MockServerImplementation;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Interaction;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.example.priceservice.grpc.Price;
import com.example.priceservice.grpc.PriceServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static au.com.dius.pact.consumer.dsl.PactBuilder.filePath;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "price-service-provider-grpc", providerType = ProviderType.SYNCH_MESSAGE, pactVersion = PactSpecVersion.V4)
@MockServerConfig(
        implementation = MockServerImplementation.Plugin,
        registryEntry = "protobuf/transport/grpc"
)
public class GrpcPriceServicePactTest {

    @Pact(consumer = "new-price-service-consumer")
    public V4Pact getAllPricesPact(PactBuilder builder) {
        return builder
                .usingPlugin("protobuf")
                .given("prices exist")
                .expectsToReceive("get all prices", "core/interaction/synchronous-message")
                .with(Map.of(
                        "pact:proto", filePath("../proto/price_service.proto"),
                        "pact:content-type", "application/grpc",
                        "pact:proto-service", "PriceService/GetAllPrices",
                        "request", Map.of(
                        ),
                        "response", List.of(Map.of(
                                "prices", List.of(Map.of(
                                        "instrument_id", "matching(type, 'AAPL')",
                                        "bid_price", "matching(number, 175.50)",
                                        "ask_price", "matching(number, 175.75)"
                                )),
                                "page", "matching(number, 1)",
                                "size", "matching(number, 1)"
                        ))
                ))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getAllPricesPact", providerType = ProviderType.SYNCH_MESSAGE)
    void testGetAllPrices(MockServer mockServer, V4Interaction.SynchronousMessages interaction) {
        var optional = getClient(mockServer).getAllPrices(null, null);
        assertThat(optional.isPresent()).isTrue();
        var response = optional.get();
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(1);
        List<Price> allPrices = response.getPricesList();
        assertThat(allPrices).hasSize(1);
        assertThat(allPrices.get(0).getInstrumentId()).isEqualTo("AAPL");
        assertThat(allPrices.get(0).getBidPrice()).isEqualTo(175.50);
        assertThat(allPrices.get(0).getAskPrice()).isEqualTo(175.75);
        assertThat(allPrices.get(0).getLastUpdated()).isNotNull();
    }

    @Pact(consumer = "new-price-service-consumer")
    public V4Pact getPricePact(PactBuilder builder) {
        return builder
                .usingPlugin("protobuf")
                .given("price with ID exists", Map.of("instrumentId", "AAPL"))
                .expectsToReceive("get price", "core/interaction/synchronous-message")
                .with(Map.of(
                        "pact:proto", filePath("../proto/price_service.proto"),
                        "pact:content-type", "application/grpc",
                        "pact:proto-service", "PriceService/GetPrice",
                        "request", Map.of(
                                "instrument_id", "AAPL"
                        ),
                        "response", List.of(Map.of(
                                "price", Map.of(
                                        "instrument_id", "AAPL",
                                        "bid_price", "matching(number, 175.50)",
                                        "ask_price", "matching(number, 175.75)"
                                )
                        ))
                ))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getPricePact", providerType = ProviderType.SYNCH_MESSAGE)
    void testGetPrice(MockServer mockServer, V4Interaction.SynchronousMessages interaction) {
        var price = getClient(mockServer).getPrice("AAPL");
        assertThat(interaction.getProviderStates()).hasSize(1);
        assertThat(price.isPresent()).isTrue();
        assertThat(price.get().getInstrumentId()).isEqualTo("AAPL");
        assertThat(price.get().getBidPrice()).isEqualTo(175.50);
        assertThat(price.get().getAskPrice()).isEqualTo(175.75);
        assertThat(price.get().getLastUpdated()).isNotNull();
    }

    @Pact(consumer = "new-price-service-consumer")
    public V4Pact getPriceNotFoundPact(PactBuilder builder) {
        return builder
                .usingPlugin("protobuf")
                .given("price with ID UNKNOWN does not exist")
                .expectsToReceive("price not found", "core/interaction/synchronous-message")
                .with(Map.of(
                        "pact:proto", filePath("../proto/price_service.proto"),
                        "pact:content-type", "application/grpc",
                        "pact:proto-service", "PriceService/GetPrice",
                        "request", Map.of(
                                "instrument_id", "UNKNOWN"
                        ),
                        "responseMetadata", Map.of(
                                "grpc-status", "NOT_FOUND",
                                "grpc-message", "matching(type, 'Price not found')"
                        )
                ))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getPriceNotFoundPact", providerType = ProviderType.SYNCH_MESSAGE)
    void testGetPriceNotFound(MockServer mockServer, V4Interaction.SynchronousMessages interaction) {
        var price = getClient(mockServer).getPrice("UNKNOWN");
        assertThat(price).isEmpty();
    }

    private GrpcPriceClient getClient(MockServer mockServer) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget("127.0.0.1:" + mockServer.getPort())
                .usePlaintext()
                .build();
        PriceServiceGrpc.PriceServiceBlockingStub stub = PriceServiceGrpc.newBlockingStub(channel);
        GrpcPriceClient grpcPriceClient = new GrpcPriceClient();
        grpcPriceClient.setPriceServiceStub(stub);
        return grpcPriceClient;
    }
}
