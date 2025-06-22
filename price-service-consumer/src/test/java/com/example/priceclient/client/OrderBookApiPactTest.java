package com.example.priceclient.client;

import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit.MockServerConfig;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.example.priceservice.client.api.OrderBookApi;
import com.example.priceservice.client.api.model.OrderBookDto;
import com.example.priceservice.client.api.model.OrderDto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pact consumer test for the Order Book API.
 * This test defines the contract between the consumer and provider for order book operations.
 * <p>
 * Тест потребителя Pact для API стакана заявок.
 * Этот тест определяет контракт между потребителем и поставщиком для операций со стаканом заявок.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "price-service.base-url=http://localhost:9091",
        "price-service.username=admin",
        "price-service.password=password",
        "spring.autoconfigure.exclude=net.devh.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration",
})
@MockServerConfig(port = "9091")
@ExtendWith(PactConsumerTestExt.class)
@Execution(ExecutionMode.SAME_THREAD)
@PactTestFor(providerName = "price-service-provider-orderbook")
public class OrderBookApiPactTest {
    @Autowired
    private OrderBookApi orderBookApi;

    /**
     * Authentication credentials for secured endpoints.
     * <p>
     * Учетные данные аутентификации для защищенных конечных точек.
     */
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "password";
    private static final String AUTH_HEADER = "Basic " + Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes());
    public static final String ISO_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    // format has precision up to millis so we truncate to millis
    // в формате точность до миллисекунд, поэтому мы обрезаем до миллисекунд
    private static final Instant timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    /**
     * Setup method to disable HTTP keep-alive for tests.
     * This helps prevent connection issues during test execution.
     * <p>
     * Метод настройки для отключения HTTP keep-alive для тестов.
     * Это помогает предотвратить проблемы с соединением во время выполнения тестов.
     *
     * @see <a href=https://github.com/pact-foundation/pact-jvm/issues/342>issue</a>
     * @see <a href=https://docs.oracle.com/javase/8/docs/technotes/guides/net/http-keepalive.html>Persistent Connections Oracle doc</a>
     */
    @BeforeAll
    public static void setup() {
        System.setProperty("http.keepAlive", "false");
    }

    /**
     * Defines a contract for retrieving an order book by ID.
     * Uses type matchers and provider state parameters for flexibility.
     * <p>
     * Определяет контракт для получения стакан заявок по ID.
     * Использует матчеры типов и параметры состояния поставщика для гибкости.
     *
     * @param builder The Pact DSL builder
     * @return The defined contract
     */
    @Pact(consumer = "price-service-consumer")
    public RequestResponsePact getOrderBookPact(PactDslWithProvider builder) {
        return builder
                // Define provider state
                // Определение состояния поставщика
                .given("order book with ID exists", Map.of("instrumentId", "AAPL"))
                .uponReceiving("a request for order book with ID AAPL")
                .pathFromProviderState("/orderbook/${instrumentId}", "/orderbook/AAPL")
                .method("GET")
                .willRespondWith()
                .status(200)
                // Using type matchers for response body
                // Использование матчеры типов для тела ответа
                .body(newJsonBody(o -> {
                    o.datetime("lastUpdated", ISO_DATE_TIME_FORMAT, timestamp);
                    o.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
                    // Matches that array fields have the proper structure of elements and generate two examples for consumer
                    // Матчер для проверки, что элементы массива имеют нужную структуру и генерирует два экземпляра для консьюмера
                    o.eachLike("bidOrders", 2, bid -> {
                        bid.numberType("price", 175.50);
                        bid.numberType("volume", 100.0);
                    });
                    o.eachLike("askOrders", 2, ask -> {
                        ask.numberType("price", 175.75);
                        ask.numberType("volume", 150.0);
                    });
                }).build())
                .toPact();
    }

    /**
     * Tests the retrieval of an order book.
     * Uses flexible assertions for array sizes and non-null checks.
     * <p>
     * Тестирует получение стакана заявок.
     * Использует гибкие утверждения для размеров массивов и проверок на ненулевые значения.
     */
    @Test
    @PactTestFor(pactMethod = "getOrderBookPact")
    void testGetOrderBook() {
        var orderBook = orderBookApi.getOrderBook("AAPL");

        assertThat(orderBook.getInstrumentId()).isEqualTo("AAPL");
        assertThat(orderBook.getBidOrders()).hasSize(2);
        assertThat(orderBook.getAskOrders()).hasSize(2);
        assertThat(orderBook.getLastUpdated().toInstant()).isEqualTo(timestamp);
        assertThat(orderBook.getBidOrders().getFirst().getPrice()).isEqualByComparingTo(BigDecimal.valueOf(175.50));
        assertThat(orderBook.getBidOrders().getFirst().getVolume()).isEqualByComparingTo(BigDecimal.valueOf(100.0));
        assertThat(orderBook.getAskOrders().getFirst().getPrice()).isEqualByComparingTo(BigDecimal.valueOf(175.75));
        assertThat(orderBook.getAskOrders().getFirst().getVolume()).isEqualByComparingTo(BigDecimal.valueOf(150.0));
    }

    /**
     * Defines a contract for the 404 Not Found scenario.
     * Testing error scenarios is the best practice in contract testing.
     * <p>
     * Определяет контракт для сценария 404 Not Found.
     * Тестирование сценариев ошибок - важная практика в контрактном тестировании.
     *
     * @param builder The Pact DSL builder
     * @return The defined contract
     */
    @Pact(consumer = "price-service-consumer")
    public RequestResponsePact getOrderBookNotFoundPact(PactDslWithProvider builder) {
        return builder
                .given("order book with ID UNKNOWN does not exist")
                .uponReceiving("a request for order book with ID UNKNOWN")
                .path("/orderbook/UNKNOWN")
                .method("GET")
                .willRespondWith()
                .status(404)
                .toPact();
    }

    /**
     * Tests the 404 Not Found scenario.
     * Proper error handling is essential for robust client implementations.
     * <p>
     * Тестирует сценарий 404 Not Found.
     * Правильная обработка ошибок необходима для надежных реализаций клиента.
     */
    @Test
    @PactTestFor(pactMethod = "getOrderBookNotFoundPact")
    void testGetOrderBookNotFound() {
        assertThatThrownBy(() -> orderBookApi.getOrderBook("UNKNOWN"))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    /**
     * Defines a contract for saving an order book.
     * Includes authentication headers for secured endpoints.
     * <p>
     * Определяет контракт для сохранения стакана заявок.
     * Включает заголовки аутентификации для защищенных конечных точек.
     *
     * @param builder The Pact DSL builder
     * @return The defined contract
     */
    @Pact(consumer = "price-service-consumer")
    public RequestResponsePact saveOrderBookPact(PactDslWithProvider builder) {
        return builder
                .given("order book can be saved", Map.of("instrumentId", "AAPL"))
                .uponReceiving("an authenticated request to save order book for AAPL")
                .pathFromProviderState("/orderbook/${instrumentId}", "/orderbook/AAPL")
                .method("POST")
                .headers("Content-Type", "application/json")
                // Including authentication header - best practice for secured endpoints
                // Включение заголовка аутентификации - лучшая практика для защищенных конечных точек
                .headerFromProviderState("Authorization", "Basic ${basicAuth}", AUTH_HEADER)
                .body(newJsonBody(o -> {
                    o.datetime("lastUpdated", ISO_DATE_TIME_FORMAT, timestamp);
                    o.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
                    o.eachLike("bidOrders", 2, bid -> {
                        bid.numberType("price", 176.50);
                        bid.numberType("volume", 120.0);
                    });
                    o.eachLike("askOrders", 2, ask -> {
                        ask.numberType("price", 176.75);
                        ask.numberType("volume", 170.0);
                    });
                }).build())
                .willRespondWith()
                .status(200)
                .body(newJsonBody(o -> {
                    // If the consumer doesn't need fields from the response body, you can remove checking and use only JSON body check
                    // Если потребителю не нужны поля из тела ответа, то убрать их проверки и использовать только проверку на JSON тело
                    o.datetime("lastUpdated", ISO_DATE_TIME_FORMAT, timestamp);
                    o.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
                    o.eachLike("bidOrders", 2, bid -> {
                        bid.numberType("price", 176.50);
                        bid.numberType("volume", 120.0);
                    });
                    o.eachLike("askOrders", 2, ask -> {
                        ask.numberType("price", 176.75);
                        ask.numberType("volume", 170.0);
                    });
                }).build())
                .toPact();
    }

    /**
     * Tests saving an order book with authentication.
     * Verifies the response structure after a successful save operation.
     * <p>
     * Тестирует сохранение стакана заявок с аутентификацией.
     * Проверяет структуру ответа после успешной операции сохранения.
     */
    @Test
    @PactTestFor(pactMethod = "saveOrderBookPact")
    void testSaveOrderBook() {
        OrderBookDto orderBookDto = new OrderBookDto()
                .instrumentId("AAPL")
                .bidOrders(List.of(
                        createOrderDto(new BigDecimal("176.50"), new BigDecimal("120.0")),
                        createOrderDto(new BigDecimal("176.45"), new BigDecimal("220.0"))
                )).askOrders(List.of(
                        createOrderDto(new BigDecimal("176.75"), new BigDecimal("170.0")),
                        createOrderDto(new BigDecimal("176.80"), new BigDecimal("270.0"))
                )).lastUpdated(timestamp.atOffset(ZoneOffset.UTC));

        var savedOrderBook = orderBookApi.saveOrderBook(orderBookDto.getInstrumentId(), orderBookDto);

        assertThat(savedOrderBook.getInstrumentId()).isEqualTo("AAPL");
        assertThat(savedOrderBook.getBidOrders()).hasSize(2);
        assertThat(savedOrderBook.getAskOrders()).hasSize(2);
        assertThat(savedOrderBook.getLastUpdated().toInstant()).isEqualTo(timestamp);
        assertThat(savedOrderBook.getBidOrders().getFirst().getPrice()).isEqualByComparingTo(BigDecimal.valueOf(176.50));
        assertThat(savedOrderBook.getBidOrders().getFirst().getVolume()).isEqualByComparingTo(BigDecimal.valueOf(120.0));
        assertThat(savedOrderBook.getAskOrders().getFirst().getPrice()).isEqualByComparingTo(BigDecimal.valueOf(176.75));
        assertThat(savedOrderBook.getAskOrders().getFirst().getVolume()).isEqualByComparingTo(BigDecimal.valueOf(170.0));
    }

    /**
     * Defines a contract for the 401 Unauthorized scenarios.
     * Testing authentication failures are important for security.
     * <p>
     * Определяет контракт для сценария 401 Unauthorized.
     * Тестирование сбоев аутентификации важно для безопасности.
     *
     * @param builder The Pact DSL builder
     * @return The defined contract
     */
    @Pact(consumer = "price-service-consumer")
    public RequestResponsePact saveOrderBookWithWrongAuthPact(PactDslWithProvider builder) {
        return builder
                .given("order book can be saved")
                .uponReceiving("an unauthenticated request to save order book")
                .pathFromProviderState("/orderbook/${instrumentId}", "/orderbook/AAPL")
                .method("POST")
                .headers("Content-Type", "application/json")
                // Deliberately omitting authentication header to test 401 response
                // Намеренное опускание заголовка аутентификации для тестирования ответа 401
                .body(newJsonBody(o -> {
                    o.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
                    o.datetime("lastUpdated", ISO_DATE_TIME_FORMAT, timestamp);
                    o.eachLike("bidOrders", 2, bid -> {
                        bid.numberType("price", 176.50);
                        bid.numberType("volume", 120.0);
                    });
                    o.eachLike("askOrders", 2, bid -> {
                        bid.numberType("price", 176.75);
                        bid.numberType("volume", 170.0);
                    });
                }).build())
                .willRespondWith()
                .status(401)
                .toPact();
    }

    /**
     * Tests the 401 Unauthorized scenario.
     * Verifies that unauthorized requests are properly rejected.
     * <p>
     * Тестирует сценарий 401 Unauthorized.
     * Проверяет, что неавторизованные запросы правильно отклоняются.
     */
    @Test
    @PactTestFor(pactMethod = "saveOrderBookWithWrongAuthPact")
    void testSaveOrderBookWithoutAuth() {
        OrderBookDto orderBookDto = new OrderBookDto()
                .instrumentId("AAPL")
                .bidOrders(List.of(
                        createOrderDto(new BigDecimal("176.50"), new BigDecimal("120.0"))
                )).askOrders(List.of(
                        createOrderDto(new BigDecimal("176.75"), new BigDecimal("170.0"))
                )).lastUpdated(timestamp.atOffset(ZoneOffset.UTC));

        assertThatThrownBy(() -> orderBookApi.saveOrderBook(orderBookDto.getInstrumentId(), orderBookDto))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    /**
     * Helper method to create order DTOs for testing.
     * <p>
     * Вспомогательный метод для создания DTO заказов для тестирования.
     *
     * @param price  The order price
     * @param volume The order volume
     * @return A new OrderDto instance
     */
    private OrderDto createOrderDto(BigDecimal price, BigDecimal volume) {
        OrderDto orderDto = new OrderDto();
        orderDto.setPrice(price);
        orderDto.setVolume(volume);
        return orderDto;
    }
}
