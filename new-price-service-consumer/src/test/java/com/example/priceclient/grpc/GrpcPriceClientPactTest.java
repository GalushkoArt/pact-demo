package com.example.priceclient.grpc;

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
import com.example.priceservice.grpc.GetAllPricesRequest;
import com.example.priceservice.grpc.GetAllPricesResponse;
import com.example.priceservice.grpc.GetPriceRequest;
import com.example.priceservice.grpc.Price;
import com.example.priceservice.grpc.PriceServiceGrpc;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static au.com.dius.pact.consumer.dsl.PactBuilder.filePath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pact consumer tests for the gRPC PriceService.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "price-service-provider-grpc", providerType = ProviderType.SYNCH_MESSAGE, pactVersion = PactSpecVersion.V4)
public class GrpcPriceClientPactTest {

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
                                "page", "matching(number, 1)",
                                "size", "matching(number, 2)"
                        ),
                        "response", List.of(Map.of(
                                "prices", List.of(Map.of(
                                        "instrument_id", "matching(type, 'AAPL')",
                                        "bid_price", "matching(number, 100.0)",
                                        "ask_price", "matching(number, 101.0)",
                                        "last_updated", Map.of(
                                                "seconds", "matching(number, 0)",
                                                "nanos", "matching(number, 0)"
                                        )
                                )),
                                "total_count", "matching(number, 1)",
                                "page", "matching(number, 1)",
                                "size", "matching(number, 2)"
                        ))
                ))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getAllPricesPact")
    @MockServerConfig(implementation = MockServerImplementation.Plugin, registryEntry = "protobuf/transport/grpc")
    void testGetAllPrices(MockServer mockServer, V4Interaction.SynchronousMessages interaction) throws InvalidProtocolBufferException {
        ManagedChannel channel = ManagedChannelBuilder.forTarget("127.0.0.1:" + mockServer.getPort())
                .usePlaintext()
                .build();
        PriceServiceGrpc.PriceServiceBlockingStub stub = PriceServiceGrpc.newBlockingStub(channel);

        GetAllPricesRequest request = GetAllPricesRequest.parseFrom(interaction.getRequest().getContents().getValue());
        GetAllPricesResponse response = stub.getAllPrices(request);
        assertThat(response.getPricesList()).isNotEmpty();
        channel.shutdownNow();
    }

    @Pact(consumer = "new-price-service-consumer")
    public V4Pact getPricePact(PactBuilder builder) {
        return builder
                .usingPlugin("protobuf")
                .given("price with ID exists")
                .expectsToReceive("get price", "core/interaction/synchronous-message")
                .with(Map.of(
                        "pact:proto", filePath("../proto/price_service.proto"),
                        "pact:content-type", "application/grpc",
                        "pact:proto-service", "PriceService/GetPrice",
                        "request", Map.of(
                                "instrument_id", "matching(type, 'AAPL')"
                        ),
                        "response", List.of(Map.of(
                                "price", Map.of(
                                        "instrument_id", "matching(type, 'AAPL')",
                                        "bid_price", "matching(number, 100.0)",
                                        "ask_price", "matching(number, 101.0)",
                                        "last_updated", Map.of(
                                                "seconds", "matching(number, 0)",
                                                "nanos", "matching(number, 0)"
                                        )
                                )
                        ))
                ))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getPricePact")
    @MockServerConfig(implementation = MockServerImplementation.Plugin, registryEntry = "protobuf/transport/grpc")
    void testGetPrice(MockServer mockServer, V4Interaction.SynchronousMessages interaction) throws InvalidProtocolBufferException {
        ManagedChannel channel = ManagedChannelBuilder.forTarget("127.0.0.1:" + mockServer.getPort())
                .usePlaintext()
                .build();
        PriceServiceGrpc.PriceServiceBlockingStub stub = PriceServiceGrpc.newBlockingStub(channel);

        GetPriceRequest request = GetPriceRequest.parseFrom(interaction.getRequest().getContents().getValue());
        Price price = stub.getPrice(request).getPrice();
        assertThat(price.getInstrumentId()).isEqualTo("AAPL");
        channel.shutdownNow();
    }

    @Pact(consumer = "new-price-service-consumer")
    public V4Pact getPriceNotFoundPact(PactBuilder builder) {
        return builder
                .usingPlugin("protobuf")
                .given("price with ID UNKNOWN does not exist")
                .expectsToReceive("get price not found", "core/interaction/synchronous-message")
                .with(Map.of(
                        "pact:proto", filePath("../proto/price_service.proto"),
                        "pact:content-type", "application/grpc",
                        "pact:proto-service", "PriceService/GetPrice",
                        "request", Map.of(
                                "instrument_id", "matching(type, 'UNKNOWN')"
                        ),
                        "responseMetadata", Map.of(
                                "grpc-status", "NOT_FOUND",
                                "grpc-message", "matching(type, 'Price not found')"
                        )
                ))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getPriceNotFoundPact")
    @MockServerConfig(implementation = MockServerImplementation.Plugin, registryEntry = "protobuf/transport/grpc")
    void testGetPriceNotFound(MockServer mockServer, V4Interaction.SynchronousMessages interaction) throws InvalidProtocolBufferException {
        ManagedChannel channel = ManagedChannelBuilder.forTarget("127.0.0.1:" + mockServer.getPort())
                .usePlaintext()
                .build();
        PriceServiceGrpc.PriceServiceBlockingStub stub = PriceServiceGrpc.newBlockingStub(channel);

        GetPriceRequest request = GetPriceRequest.parseFrom(interaction.getRequest().getContents().getValue());
        assertThatThrownBy(() -> stub.getPrice(request)).isInstanceOf(StatusRuntimeException.class);
        channel.shutdownNow();
    }
}
