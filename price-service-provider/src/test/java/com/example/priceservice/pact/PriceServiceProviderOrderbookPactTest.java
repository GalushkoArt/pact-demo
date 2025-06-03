package com.example.priceservice.pact;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.StateChangeAction;
import au.com.dius.pact.provider.junitsupport.loader.*;
import com.example.priceservice.adapter.persistence.entity.OrderBookEntity;
import com.example.priceservice.adapter.persistence.entity.OrderEntity;
import com.example.priceservice.adapter.persistence.repository.OrderBookJpaRepository;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.hc.core5.http.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Provider-side contract verification test for the Order Book API.
 * This test verifies that the provider can fulfill the contracts defined by consumers.
 * Pact contracts are provided to the provider from the Pact Broker.
 * <p>
 * Тест проверки контракта на стороне поставщика для API стакана заявок.
 * Этот тест проверяет, что поставщик может выполнить контракты, определенные потребителями.
 * Пакт контракты предоставляются провайдеру из Пакт Брокера.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, value = {
        "admin.username=admin",
        "admin.password=password",
})
@Provider("price-service-provider-orderbook")
@PactBroker(
        url = "${PACT_URL:http://localhost:9292}",
        authentication = @PactBrokerAuth(username = "${PACT_BROKER_USER:pact}", password = "${PACT_BROKER_PASSWORD:pact}"),
        providerBranch = "${GIT_BRANCH:master}"
)
@VersionSelector
public class PriceServiceProviderOrderbookPactTest {
    private static final Logger log = LoggerFactory.getLogger(PriceServiceProviderOrderbookPactTest.class);

    @LocalServerPort
    private int port;

    @Autowired
    private OrderBookJpaRepository orderBookJpaRepository;

    /**
     * Authentication credentials for secured endpoints.
     * These match the credentials in the consumer tests.
     * <p>
     * Учетные данные аутентификации для защищенных конечных точек.
     * Они соответствуют учетным данным в тестах потребителя.
     */
    private static final String username = "admin";
    private static final String password = "password";
    private static final String AUTH_HEADER = "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());

    /**
     * Defines which consumer versions to verify against.
     * This is the best practice to ensure you're testing it against relevant consumer versions.
     * <p>
     * Определяет, какие версии потребителей проверять.
     * Это лучшая практика для обеспечения тестирования против релевантных версий потребителей.
     *
     * @return A selector builder with configured version selectors
     */
    @PactBrokerConsumerVersionSelectors
    public static SelectorBuilder consumerVersionSelectors() {
        return new SelectorBuilder()
                .mainBranch()
                .tag("mobile")
                .latestTag("dev");
    }

    /**
     * Sets up the test target before each verification.
     * <p>
     * Настраивает целевой объект тестирования перед каждой проверкой.
     *
     * @param context The Pact verification context
     */
    @BeforeEach
    void setUp(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", port));
    }

    /**
     * Template method for all Pact verifications.
     * Handles authentication header replacement for each request.
     * <p>
     * Шаблонный метод для всех проверок Pact.
     * Обрабатывает замену заголовка аутентификации для каждого запроса.
     *
     * @param context The verification context
     * @param request The HTTP request being verified
     */
    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void testTemplate(PactVerificationContext context, HttpRequest request) {
        replaceAuthHeader(request);
        context.verifyInteraction();
    }

    /**
     * Replaces the authentication header in the request.
     * This is the best practice for handling authentication in provider tests.
     * <p>
     * Заменяет заголовок аутентификации в запросе.
     * Это лучшая практика для обработки аутентификации в тестах поставщика.
     *
     * @param request The HTTP request to modify
     */
    private void replaceAuthHeader(HttpRequest request) {
        if (request.containsHeader("Authorization")) {
            request.removeHeaders("Authorization");
            request.addHeader("Authorization", AUTH_HEADER);
        } else {
            log.warn("Request does not contain Authorization header. Skipping authorization header replacement.");
        }
    }

    /**
     * Sets up the "order book with ID exists" provider state.
     * Creates a complete order book with bid and ask orders for testing.
     * Uses dynamic state parameters for flexible testing.
     * <p>
     * Настраивает состояние поставщика "стакан заявок с ID существует".
     * Создает полный стакан с заявками на покупку и продажу для тестирования.
     * Использует динамические параметры состояния для гибкого тестирования.
     *
     * @return A map of state parameters
     */
    @State(value = "order book with ID exists", action = StateChangeAction.SETUP)
    @Transactional
    public Map<String, String> orderBookWithIdExists() {
        // Clear existing data for this ID
        // Очистка существующих данных для этого ID
        var parameters = new HashMap<String, String>();
        var instrumentId = parameters.computeIfAbsent("instrumentId", id -> RandomStringUtils.secure().nextAlphanumeric(4));
        orderBookJpaRepository.findById(instrumentId).ifPresent(orderBook -> orderBookJpaRepository.delete(orderBook));

        // Create test data
        // Создание тестовых данных
        OrderBookEntity orderBook = OrderBookEntity.builder()
                .instrumentId(instrumentId)
                .lastUpdated(Instant.now())
                .build();

        // Create bid orders
        // Создание заявок на покупку
        List<OrderEntity> bidOrders = new ArrayList<>();
        bidOrders.add(OrderEntity.builder()
                .orderBook(orderBook)
                .price(new BigDecimal("175.50"))
                .volume(new BigDecimal("100"))
                .orderType(OrderEntity.OrderType.BID)
                .build());
        bidOrders.add(OrderEntity.builder()
                .orderBook(orderBook)
                .price(new BigDecimal("175.45"))
                .volume(new BigDecimal("200"))
                .orderType(OrderEntity.OrderType.BID)
                .build());
        bidOrders.add(OrderEntity.builder()
                .orderBook(orderBook)
                .price(new BigDecimal("175.40"))
                .volume(new BigDecimal("300"))
                .orderType(OrderEntity.OrderType.BID)
                .build());

        // Create ask orders
        // Создание заявок на продажу
        List<OrderEntity> askOrders = new ArrayList<>();
        askOrders.add(OrderEntity.builder()
                .orderBook(orderBook)
                .price(new BigDecimal("175.75"))
                .volume(new BigDecimal("150"))
                .orderType(OrderEntity.OrderType.ASK)
                .build());
        askOrders.add(OrderEntity.builder()
                .orderBook(orderBook)
                .price(new BigDecimal("175.80"))
                .volume(new BigDecimal("250"))
                .orderType(OrderEntity.OrderType.ASK)
                .build());
        askOrders.add(OrderEntity.builder()
                .orderBook(orderBook)
                .price(new BigDecimal("175.85"))
                .volume(new BigDecimal("350"))
                .orderType(OrderEntity.OrderType.ASK)
                .build());

        orderBook.setBidOrders(bidOrders);
        orderBook.setAskOrders(askOrders);

        orderBookJpaRepository.save(orderBook);
        return parameters;
    }

    /**
     * Cleans up after the "order book with ID exists" provider state.
     * This is the best practice to ensure test isolation.
     * <p>
     * Очищает после состояния поставщика "стакан заявок с ID существует".
     * Это лучшая практика для обеспечения изоляции тестов.
     *
     * @param parameters The state parameters from setup
     */
    @State(value = "order book with ID exists", action = StateChangeAction.TEARDOWN)
    @Transactional
    public void orderBookWithIdExistsCleanup(Map<String, String> parameters) {
        log.debug("Cleaning up order book with ID: {}", parameters.get("instrumentId"));
        Optional.ofNullable(parameters.get("instrumentId")).ifPresent(id -> orderBookJpaRepository.deleteById(id));
    }

    /**
     * Sets up the "order book can be saved" provider state.
     * Prepares for testing POST operations.
     * <p>
     * Настраивает состояние поставщика "стакан может быть сохранён".
     * Подготавливает для тестирования операций POST.
     *
     * @return A map of state parameters
     */
    @State(value = "order book can be saved", action = StateChangeAction.SETUP)
    @Transactional
    public Map<String, String> orderBookCanBeSaved() {
        // Clear existing data for this ID
        // Очистка существующих данных для этого ID
        var parameters = new HashMap<String, String>();
        var instrumentId = parameters.computeIfAbsent("instrumentId", id -> RandomStringUtils.secure().nextAlphanumeric(4));
        orderBookJpaRepository.findById(instrumentId).ifPresent(orderBook -> orderBookJpaRepository.delete(orderBook));
        return parameters;
    }

    /**
     * Sets up the "order book with ID UNKNOWN does not exist" provider state.
     * Testing error scenarios is the best practice in contract testing.
     * <p>
     * Настраивает состояние поставщика "стакан заявок с ID UNKNOWN не существует".
     * Тестирование сценариев ошибок - лучшая практика в контрактном тестировании.
     */
    @State("order book with ID UNKNOWN does not exist")
    @Transactional
    public void orderBookWithIdUnknownDoesNotExist() {
        // Ensure the order book doesn't exist
        // Убедиться, что стакана заявок не существует
        orderBookJpaRepository.findById("UNKNOWN").ifPresent(orderBook -> orderBookJpaRepository.delete(orderBook));
    }
}
