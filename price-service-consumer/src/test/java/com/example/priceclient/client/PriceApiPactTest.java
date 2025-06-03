package com.example.priceclient.client;

import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit.MockServerConfig;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.example.priceservice.client.api.PricesApi;
import com.example.priceservice.client.api.model.PriceDto;
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
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonArrayMinLike;
import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pact consumer test for the Price Service client.
 * This test defines the contract between the consumer and provider for price-related operations.
 * <p>
 * Тест потребителя Pact для клиента сервиса цен.
 * Этот тест определяет контракт между потребителем и поставщиком для операций, связанных с ценами.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "price-service.base-url=http://localhost:9090",
        "price-service.username=admin",
        "price-service.password=password",
})
@MockServerConfig(port = "9090")
@ExtendWith(PactConsumerTestExt.class)
@Execution(ExecutionMode.SAME_THREAD)
@PactTestFor(providerName = "price-service-provider-price")
public class PriceApiPactTest {
    @Autowired
    private PricesApi pricesApi;

    /**
     * Authentication credentials for secured endpoints.
     * <p>
     * Учетные данные аутентификации для защищенных конечных точек.
     */
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "password";
    private static final String AUTH_HEADER = "Basic " + Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes());

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
     * Defines a contract for retrieving all prices.
     * Uses type matchers instead of exact values for flexibility.
     * <p>
     * Определяет контракт для получения всех цен.
     * Использует матчеры типов вместо точных значений для гибкости.
     *
     * @param builder The Pact DSL builder
     * @return The defined contract
     */
    @Pact(consumer = "price-service-consumer")
    public RequestResponsePact getAllPricesPact(PactDslWithProvider builder) {
        var timestamp = Instant.now().toString();
        return builder
                // Define the provider state - a key best practice
                // Определение состояния поставщика - ключевая лучшая практика
                .given("prices exist")
                .uponReceiving("a request for all prices")
                .path("/prices")
                .method("GET")
                .willRespondWith()
                .status(200)
                // Using type matchers instead of exact values - recommended best practice
                // Использование матчеров типов вместо точных значений - рекомендуемая лучшая практика
                .body(newJsonArrayMinLike(1, array -> {
                    array.object(o -> {
                        o.stringType("instrumentId", "AAPL");
                        o.decimalType("bidPrice", 175.50);
                        o.decimalType("askPrice", 175.75);
                        o.stringType("lastUpdated", timestamp);
                    });
                }).build())
                .toPact();
    }

    /**
     * Tests the retrieval of all prices.
     * Uses flexible assertions to verify response structure without exact values.
     * <p>
     * Тестирует получение всех цен.
     * Использует гибкие утверждения для проверки структуры ответа без точных значений.
     */
    @Test
    @PactTestFor(pactMethod = "getAllPricesPact")
    void testGetAllPrices() {
        List<PriceDto> prices = pricesApi.getAllPrices();

        // Flexible assertions - verify structure without exact values
        // Гибкие утверждения - проверка структуры без точных значений
        assertThat(prices).isNotNull();
        assertThat(prices.size()).isGreaterThanOrEqualTo(1);
        assertThat(prices.get(0).getInstrumentId()).isNotNull();
        assertThat(prices.get(0).getBidPrice()).isNotNull();
        assertThat(prices.get(0).getAskPrice()).isNotNull();
        assertThat(prices.get(0).getLastUpdated()).isNotNull();
    }

    /**
     * Defines a contract for retrieving a specific price by ID.
     * Uses provider state parameters for dynamic testing.
     * <p>
     * Определяет контракт для получения конкретной цены по ID.
     * Использует параметры состояния поставщика для динамического тестирования.
     *
     * @param builder The Pact DSL builder
     * @return The defined contract
     */
    @Pact(consumer = "price-service-consumer")
    public RequestResponsePact getPricePact(PactDslWithProvider builder) {
        var timestamp = Instant.now().toString();
        return builder
                // Clear provider state definition
                // Четкое определение состояния поставщика
                .given("price with ID exists")
                .uponReceiving("a request for price with ID AAPL")
                // Using provider state parameters for path
                // Использование параметров состояния поставщика для пути
                .pathFromProviderState("/prices/${instrumentId}", "/prices/AAPL")
                .method("GET")
                .willRespondWith()
                .status(200)
                .body(newJsonBody(body -> {
                    // Using provider state values in response
                    // Использование значений состояния поставщика в ответе
                    body.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
                    body.decimalType("bidPrice", 175.50);
                    body.decimalType("askPrice", 175.75);
                    body.stringType("lastUpdated", timestamp);
                }).build())
                .toPact();
    }

    /**
     * Tests the retrieval of a specific price.
     * Combines exact value assertions with flexible type assertions.
     * <p>
     * Тестирует получение конкретной цены.
     * Сочетает утверждения точных значений с гибкими утверждениями типов.
     */
    @Test
    @PactTestFor(pactMethod = "getPricePact")
    void testGetPrice() {
        PriceDto price = pricesApi.getPrice("AAPL");

        // Exact assertion for ID, flexible assertions for other fields
        // Точное утверждение для ID, гибкие утверждения для других полей
        assertThat(price.getInstrumentId()).isEqualTo("AAPL");
        assertThat(price.getBidPrice()).isNotNull();
        assertThat(price.getAskPrice()).isNotNull();
        assertThat(price.getLastUpdated()).isNotNull();
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
    public RequestResponsePact getPriceNotFoundPact(PactDslWithProvider builder) {
        return builder
                .given("price with ID UNKNOWN does not exist")
                .uponReceiving("a request for price with ID UNKNOWN")
                .path("/prices/UNKNOWN")
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
    @PactTestFor(pactMethod = "getPriceNotFoundPact")
    void testGetPriceNotFound() {
        assertThatThrownBy(() -> pricesApi.getPrice("UNKNOWN"))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    /**
     * Defines a contract for saving a price.
     * Includes authentication headers for secured endpoints.
     * <p>
     * Определяет контракт для сохранения цены.
     * Включает заголовки аутентификации для защищенных конечных точек.
     *
     * @param builder The Pact DSL builder
     * @return The defined contract
     */
    @Pact(consumer = "price-service-consumer")
    public RequestResponsePact savePricePact(PactDslWithProvider builder) {
        var timestamp = Instant.now().toString();

        return builder
                .given("price can be saved")
                .uponReceiving("an authenticated request to save price for AAPL")
                .pathFromProviderState("/prices/${instrumentId}", "/prices/AAPL")
                .method("POST")
                // Including authentication header - best practice for secured endpoints
                // Включение заголовка аутентификации - лучшая практика для защищенных конечных точек
                .headers("Authorization", AUTH_HEADER)
                .headers("Content-Type", "application/json")
                .body(newJsonBody(body -> {
                    body.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
                    body.decimalType("bidPrice", 176.50);
                    body.decimalType("askPrice", 176.75);
                    body.stringType("lastUpdated", timestamp);
                }).build())
                .willRespondWith()
                .status(200)
                .body(newJsonBody(body -> {
                    body.stringType("instrumentId", "AAPL");
                    body.decimalType("bidPrice", 176.50);
                    body.decimalType("askPrice", 176.75);
                    body.stringType("lastUpdated", timestamp);
                }).build())
                .toPact();
    }

    /**
     * Tests saving a price with authentication.
     * Verifies the response structure after a successful save operation.
     * <p>
     * Тестирует сохранение цены с аутентификацией.
     * Проверяет структуру ответа после успешной операции сохранения.
     */
    @Test
    @PactTestFor(pactMethod = "savePricePact")
    void testSavePrice() {
        PriceDto priceDto = new PriceDto();
        priceDto.setInstrumentId("AAPL");
        priceDto.setBidPrice(new BigDecimal("176.50"));
        priceDto.setAskPrice(new BigDecimal("176.75"));
        priceDto.setLastUpdated(OffsetDateTime.now());

        PriceDto savedPrice = pricesApi.savePrice(priceDto.getInstrumentId(), priceDto);

        // Exact assertions for critical fields
        // Точные утверждения для критических полей
        assertThat(savedPrice.getInstrumentId()).isEqualTo("AAPL");
        assertThat(savedPrice.getBidPrice()).isEqualByComparingTo(new BigDecimal("176.50"));
        assertThat(savedPrice.getAskPrice()).isEqualByComparingTo(new BigDecimal("176.75"));
    }

    /**
     * Defines a contract for the 401 Unauthorized scenarios when saving.
     * Testing authentication failures are important for security.
     * <p>
     * Определяет контракт для сценария 401 Unauthorized при сохранении.
     * Тестирование сбоев аутентификации важно для безопасности.
     *
     * @param builder The Pact DSL builder
     * @return The defined contract
     */
    @Pact(consumer = "price-service-consumer")
    public RequestResponsePact saveWithWrongAuthPact(PactDslWithProvider builder) {
        var timestamp = Instant.now().toString();

        return builder
                .given("price can be saved")
                .uponReceiving("an unauthenticated request to save price")
                .pathFromProviderState("/prices/${instrumentId}", "/prices/AAPL")
                .method("POST")
                // Deliberately omitting authentication header to test 401 response
                // Намеренное опускание заголовка аутентификации для тестирования ответа 401
                .headers("Content-Type", "application/json")
                .body(newJsonBody(body -> {
                    body.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
                    body.decimalType("bidPrice", 176.50);
                    body.decimalType("askPrice", 176.75);
                    body.stringType("lastUpdated", timestamp);
                }).build())
                .willRespondWith()
                .status(401)
                .toPact();
    }

    /**
     * Tests the 401 Unauthorized scenario when saving.
     * Verifies that unauthorized requests are properly rejected.
     * <p>
     * Тестирует сценарий 401 Unauthorized при сохранении.
     * Проверяет, что неавторизованные запросы правильно отклоняются.
     */
    @Test
    @PactTestFor(pactMethod = "saveWithWrongAuthPact")
    void testSavePriceWithoutAuth() {
        PriceDto priceDto = new PriceDto();
        priceDto.setInstrumentId("AAPL");
        priceDto.setBidPrice(new BigDecimal("176.50"));
        priceDto.setAskPrice(new BigDecimal("176.75"));
        priceDto.setLastUpdated(OffsetDateTime.now());

        assertThatThrownBy(() -> pricesApi.savePrice(priceDto.getInstrumentId(), priceDto))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    /**
     * Defines a contract for deleting a price.
     * Includes authentication headers for secured endpoints.
     * <p>
     * Определяет контракт для удаления цены.
     * Включает заголовки аутентификации для защищенных конечных точек.
     *
     * @param builder The Pact DSL builder
     * @return The defined contract
     */
    @Pact(consumer = "price-service-consumer")
    public RequestResponsePact deletePricePact(PactDslWithProvider builder) {
        return builder
                .given("price with ID exists")
                .uponReceiving("an authenticated request to delete price with ID AAPL")
                .pathFromProviderState("/prices/${instrumentId}", "/prices/AAPL")
                .method("DELETE")
                // Including authentication header for secured endpoint
                // Включение заголовка аутентификации для защищенной конечной точки
                .headers("Authorization", AUTH_HEADER)
                .willRespondWith(response -> response.status(204))
                .toPact();
    }

    /**
     * Tests deleting a price with authentication.
     * Verifies the response status for a successful delete operation.
     * <p>
     * Тестирует удаление цены с аутентификацией.
     * Проверяет статус ответа для успешной операции удаления.
     */
    @Test
    @PactTestFor(pactMethod = "deletePricePact")
    void testDeletePrice() {
        pricesApi.deletePriceWithResponseSpec("AAPL").toBodilessEntity();
    }

    /**
     * Defines a contract for the 401 Unauthorized scenarios when deleting.
     * Testing authentication failures for all secured operations is the best practice.
     * <p>
     * Определяет контракт для сценария 401 Unauthorized при удалении.
     * Тестирование сбоев аутентификации для всех защищенных операций - лучшая практика.
     *
     * @param builder The Pact DSL builder
     * @return The defined contract
     */
    @Pact(consumer = "price-service-consumer")
    public RequestResponsePact deleteWithWrongAuthPact(PactDslWithProvider builder) {
        return builder
                .given("price with ID exists")
                .uponReceiving("an unauthenticated request to delete price")
                .pathFromProviderState("/prices/${instrumentId}", "/prices/AAPL")
                .method("DELETE")
                // Deliberately omitting authentication header to test 401 response
                // Намеренное опускание заголовка аутентификации для тестирования ответа 401
                .willRespondWith()
                .status(401)
                .toPact();
    }

    /**
     * Tests the 401 Unauthorized scenario when deleting.
     * Verifies that unauthorized requests are properly rejected.
     * <p>
     * Тестирует сценарий 401 Unauthorized при удалении.
     * Проверяет, что неавторизованные запросы правильно отклоняются.
     */
    @Test
    @PactTestFor(pactMethod = "deleteWithWrongAuthPact")
    void testDeletePriceWithoutAuth() {
        assertThatThrownBy(() -> pricesApi.deletePrice("AAPL"))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }
}
