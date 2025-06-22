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
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static au.com.dius.pact.consumer.dsl.PactBuilder.filePath;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Consumer Pact tests for the gRPC PriceService.
 * Uses the Pact gRPC plugin for request/response message verification.
 * <p>
 * Тесты потребителя Pact для gRPC сервиса PriceService.
 * Использует плагин Pact gRPC для проверки сообщений.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "price-service-provider-grpc", providerType = ProviderType.SYNCH_MESSAGE, pactVersion = PactSpecVersion.V4)
@MockServerConfig(
        implementation = MockServerImplementation.Plugin,
        registryEntry = "protobuf/transport/grpc"
)
public class GrpcPriceServicePactTest {
    private static final Instant timestamp = Instant.now();

    /**
     * Defines a contract for the GetAllPrices gRPC call.
     * <p>
     * Определяет контракт для gRPC вызова GetAllPrices.
     *
     * @see <a href=https://github.com/pact-foundation/pact-plugins/blob/main/docs/matching-rule-definition-expressions.md>link to matchers docs/ссылка на документацию по матчерам</a>
     */
    @Pact(consumer = "price-service-consumer")
    public V4Pact getAllPricesPact(PactBuilder builder) {
        return builder
                .usingPlugin("protobuf")
                .given("prices exist")
                .given("valid token")
                .expectsToReceive("get all prices", "core/interaction/synchronous-message")
                .with(Map.of(
                        "pact:proto", filePath("../proto/price_service.proto"),
                        "pact:content-type", "application/grpc",
                        "pact:proto-service", "PriceService/GetAllPrices",
                        "requestMetadata", Map.of("authorization", "matching(regex, 'Bearer [\\w-]{6,}', 'Bearer valid-token')"),
                        "responseMetadata", Map.of(
                                "x-authenticated", "matching(type, 'true')"
                        ),
                        "request", Map.of(
                        ),
                        "response", List.of(Map.of(
                                "prices", List.of(Map.of(
                                        "instrument_id", "matching(type, 'AAPL')",
                                        "bid_price", "matching(number, 175.50)",
                                        "ask_price", "matching(number, 175.75)"
                                        // by proto definition there is last_updated field, but we don't need it, so we skipped it
                                        // в определении прото договора есть поле last_updated, но оно нам не нужно, поэтому мы его пропустили
                                )),
                                "page", "matching(number, 1)",
                                "size", "matching(number, 1)"
                        ))
                ))
                .toPact();
    }

    /**
     * Tests the GetAllPrices call using the Pact mock server.
     * <p>
     * Тестирует вызов GetAllPrices через Pact mock server.
     */
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
        assertThat(allPrices.getFirst().getInstrumentId()).isEqualTo("AAPL");
        assertThat(allPrices.getFirst().getBidPrice()).isEqualTo(175.50);
        assertThat(allPrices.getFirst().getAskPrice()).isEqualTo(175.75);
    }

    /**
     * Defines a contract for the GetPrice gRPC call with a valid ID.
     * <p>
     * Определяет контракт для gRPC вызова GetPrice с существующим ID.
     */
    @Pact(consumer = "price-service-consumer")
    public V4Pact getPricePact(PactBuilder builder) {
        return builder
                .usingPlugin("protobuf")
                .given("price with ID exists", Map.of("instrumentId", "AAPL"))
                .expectsToReceive("get price", "core/interaction/synchronous-message")
                .with(Map.of(
                        "pact:proto", filePath("../proto/price_service.proto"),
                        "pact:content-type", "application/grpc",
                        "pact:proto-service", "PriceService/GetPrice",
                        "requestMetadata", Map.of("authorization", "matching(regex, 'Bearer [\\w-]{6,}', 'Bearer valid-token')"),
                        "responseMetadata", Map.of(
                                "x-authenticated", "matching(type, 'true')"
                        ),
                        "request", Map.of(
                                "instrument_id", "AAPL"
                        ),
                        "response", List.of(Map.of(
                                "price", Map.of(
                                        "instrument_id", "AAPL",
                                        "bid_price", "matching(number, 175.50)",
                                        "ask_price", "matching(number, 175.75)",
                                        "last_updated", Map.of(
                                                "seconds", format("matching(integer, %d)", timestamp.getEpochSecond()),
                                                "nanos", format("matching(integer, %d)", timestamp.getNano())
                                        )
                                )
                        ))
                ))
                .toPact();
    }

    /**
     * Tests successful retrieval of a price over gRPC.
     * <p>
     * Тестирует успешное получение цены по gRPC.
     */
    @Test
    @PactTestFor(pactMethod = "getPricePact", providerType = ProviderType.SYNCH_MESSAGE)
    void testGetPrice(MockServer mockServer, V4Interaction.SynchronousMessages interaction) {
        var price = getClient(mockServer).getPrice("AAPL");
        assertThat(interaction.getProviderStates()).hasSize(1);
        assertThat(price.isPresent()).isTrue();
        assertThat(price.get().getInstrumentId()).isEqualTo("AAPL");
        assertThat(price.get().getBidPrice()).isEqualTo(175.50);
        assertThat(price.get().getAskPrice()).isEqualTo(175.75);
        assertThat(price.get().getLastUpdated().getSeconds()).isEqualTo(timestamp.getEpochSecond());
        assertThat(price.get().getLastUpdated().getNanos()).isEqualTo(timestamp.getNano());
    }

    /**
     * Contract for the NOT_FOUND gRPC response.
     * <p>
     * Контракт для сценария, когда цена не найдена.
     */
    @Pact(consumer = "price-service-consumer")
    public V4Pact getPriceNotFoundPact(PactBuilder builder) {
        return builder
                .usingPlugin("protobuf")
                .given("price with ID UNKNOWN does not exist")
                .expectsToReceive("price not found", "core/interaction/synchronous-message")
                .with(Map.of(
                        "pact:proto", filePath("../proto/price_service.proto"),
                        "pact:content-type", "application/grpc",
                        "pact:proto-service", "PriceService/GetPrice",
                        "requestMetadata", Map.of("authorization", "matching(regex, 'Bearer [\\w-]{6,}', 'Bearer valid-token')"),
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

    /**
     * Tests the not-found case for the GetPrice call.
     * <p>
     * Тестирует случай отсутствия цены в сервисе.
     */
    @Test
    @PactTestFor(pactMethod = "getPriceNotFoundPact", providerType = ProviderType.SYNCH_MESSAGE)
    void testGetPriceNotFound(MockServer mockServer, V4Interaction.SynchronousMessages interaction) {
        var price = getClient(mockServer).getPrice("UNKNOWN");
        assertThat(price).isEmpty();
    }

    /**
     * Contract for requests missing the authentication token.
     * <p>
     * Контракт для запроса без токена аутентификации.
     */
    @Pact(consumer = "price-service-consumer")
    public V4Pact missingTokenPact(PactBuilder builder) {
        return builder
                .usingPlugin("protobuf")
                .given("price with ID exists", Map.of("instrumentId", "AAPL"))
                .given("missing token")
                .expectsToReceive("missing token get price", "core/interaction/synchronous-message")
                .with(Map.of(
                        "pact:proto", filePath("../proto/price_service.proto"),
                        "pact:content-type", "application/grpc",
                        "pact:proto-service", "PriceService/GetPrice",
                        "request", Map.of(
                                "instrument_id", "AAPL"
                        ),
                        "responseMetadata", Map.of(
                                "grpc-status", "UNAUTHENTICATED",
                                "grpc-message", "matching(type, 'Missing token')"
                        )
                ))
                .toPact();
    }

    /**
     * Tests missing-token scenario and expects UNAUTHENTICATED status.
     * <p>
     * Тестирует сценарий отсутствующего токена и ожидает статус UNAUTHENTICATED.
     */
    @Test
    @PactTestFor(pactMethod = "missingTokenPact", providerType = ProviderType.SYNCH_MESSAGE)
    void testMissingToken(MockServer mockServer) {
        assertThatThrownBy(() -> getClient(mockServer).getPrice("AAPL"))
                .isInstanceOf(StatusRuntimeException.class)
                .matches(e -> ((StatusRuntimeException) e).getStatus().getCode() == Status.Code.UNAUTHENTICATED);
    }

    /**
     * Helper to create a gRPC client connected to the Pact mock server.
     * <p>
     * Вспомогательный метод для создания gRPC клиента, подключенного к mock-серверу Pact.
     */
    private GrpcPriceClient getClient(MockServer mockServer) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget("127.0.0.1:" + mockServer.getPort())
                .usePlaintext()
                .intercept(new TokenClientInterceptor("valid-token"))
                .build();
        PriceServiceGrpc.PriceServiceBlockingStub stub = PriceServiceGrpc.newBlockingStub(channel);
        GrpcPriceClient grpcPriceClient = new GrpcPriceClient();
        grpcPriceClient.setPriceServiceStub(stub);
        return grpcPriceClient;
    }
}
