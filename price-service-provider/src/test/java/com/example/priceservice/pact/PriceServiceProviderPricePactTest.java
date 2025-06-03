package com.example.priceservice.pact;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.StateChangeAction;
import au.com.dius.pact.provider.junitsupport.loader.*;
import com.example.priceservice.adapter.persistence.entity.PriceEntity;
import com.example.priceservice.adapter.persistence.repository.PriceJpaRepository;
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
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Provider-side contract verification test.
 * This test verifies that the provider can fulfill the contracts defined by consumers.
 * Pact contracts are provided to the provider from the Pact Broker.
 * <p>
 * Тест проверки контракта на стороне поставщика.
 * Этот тест проверяет, что поставщик может выполнить контракты, определенные потребителями.
 * Пакт контракты предоставляются провайдеру из Пакт Брокера.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, value = {
        "admin.username=admin",
        "admin.password=password",
})
@Provider("price-service-provider-price")
@PactBroker(
        url = "${PACT_URL:http://localhost:9292}",
        authentication = @PactBrokerAuth(username = "${PACT_BROKER_USER:pact}", password = "${PACT_BROKER_PASSWORD:pact}"),
        providerBranch = "${GIT_BRANCH:master}"
)
@VersionSelector
public class PriceServiceProviderPricePactTest {
    private static final Logger log = LoggerFactory.getLogger(PriceServiceProviderPricePactTest.class);

    @LocalServerPort
    private int port;

    @Autowired
    private PriceJpaRepository priceJpaRepository;

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
                .tag("prod")
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
     * Lock to ensure thread safety when setting up prices.
     * <p>
     * Блокировка для обеспечения потокобезопасности при настройке цен.
     */
    static ReentrantLock pricesExist = new ReentrantLock();

    /**
     * Sets up the "prices exist" provider state.
     * Creates sample price data for testing.
     * <p>
     * Настраивает состояние поставщика "цены существуют".
     * Создает образцы данных о ценах для тестирования.
     *
     * @throws InterruptedException If the thread is interrupted while waiting for the lock
     */
    @State(value = "prices exist", action = StateChangeAction.SETUP)
    @Transactional
    public void pricesExist() throws InterruptedException {
        // Create test data
        // Создание тестовых данных
        if (pricesExist.tryLock(30, TimeUnit.SECONDS)) {
            try {
                priceJpaRepository.findById("AAPL").ifPresentOrElse(
                        e -> {
                        },
                        () -> priceJpaRepository.save(PriceEntity.builder()
                                .instrumentId("AAPL")
                                .bidPrice(new BigDecimal("175.50"))
                                .askPrice(new BigDecimal("175.75"))
                                .lastUpdated(Instant.now())
                                .build())
                );
                priceJpaRepository.findById("MSFT").ifPresentOrElse(
                        e -> {
                        },
                        () -> priceJpaRepository.save(PriceEntity.builder()
                                .instrumentId("MSFT")
                                .bidPrice(new BigDecimal("330.25"))
                                .askPrice(new BigDecimal("330.50"))
                                .lastUpdated(Instant.now())
                                .build())
                );
            } finally {
                pricesExist.unlock();
            }
        } else {
            log.warn("Prices exist lock timed out. Skipping prices exist setup.");
        }
    }

    /**
     * Sets up the "price with ID exists" provider state.
     * Uses dynamic state parameters for flexible testing.
     * <p>
     * Настраивает состояние поставщика "цена с ID существует".
     * Использует динамические параметры состояния для гибкого тестирования.
     *
     * @return A map of state parameters
     */
    @State(value = "price with ID exists", action = StateChangeAction.SETUP)
    @Transactional
    public Map<String, String> priceWithIdExists() {
        // Clear existing data for this ID
        // Очистка существующих данных для этого ID
        var parameters = new HashMap<String, String>();
        var instrumentId = parameters.computeIfAbsent("instrumentId", id -> RandomStringUtils.secure().nextAlphanumeric(4));
        priceJpaRepository.findById(instrumentId).ifPresent(price -> priceJpaRepository.delete(price));

        // Create test data
        // Создание тестовых данных
        PriceEntity apple = PriceEntity.builder()
                .instrumentId(instrumentId)
                .bidPrice(new BigDecimal("175.50"))
                .askPrice(new BigDecimal("175.75"))
                .lastUpdated(Instant.now())
                .build();

        priceJpaRepository.save(apple);
        return parameters;
    }

    /**
     * Cleans up after the "price with ID exists" provider state.
     * This is the best practice to ensure test isolation.
     * <p>
     * Очищает после состояния поставщика "цена с ID существует".
     * Это лучшая практика для обеспечения изоляции тестов.
     *
     * @param parameters The state parameters from setup
     */
    @State(value = "price with ID exists", action = StateChangeAction.TEARDOWN)
    @Transactional
    public void priceWithIdExistsCleanup(Map<String, String> parameters) {
        log.debug("Cleaning up price with ID: {}", parameters.get("instrumentId"));
        Optional.ofNullable(parameters.get("instrumentId")).ifPresent(id -> priceJpaRepository.deleteById(id));
    }

    /**
     * Sets up the "price with ID UNKNOWN does not exist" provider state.
     * Testing error scenarios is the best practice in contract testing.
     * <p>
     * Настраивает состояние поставщика "цена с ID UNKNOWN не существует".
     * Тестирование сценариев ошибок - лучшая практика в контрактном тестировании.
     */
    @State("price with ID UNKNOWN does not exist")
    @Transactional
    public void priceWithIdUnknownDoesNotExist() {
        // Ensure the price doesn't exist
        // Убедиться, что цена не существует
        priceJpaRepository.findById("UNKNOWN").ifPresent(price -> priceJpaRepository.delete(price));
    }

    /**
     * Sets up the "price can be saved" provider state.
     * Prepares for testing POST operations.
     * <p>
     * Настраивает состояние поставщика "цена может быть сохранена".
     * Подготавливает для тестирования операций POST.
     *
     * @return A map of state parameters
     */
    @State(value = "price can be saved", action = StateChangeAction.SETUP)
    @Transactional
    public Map<String, String> priceCanBeSaved() {
        // No specific setup needed as the repository allows saving
        // Специальная настройка не требуется, так как репозиторий позволяет сохранять
        var parameters = new HashMap<String, String>();
        var instrumentId = parameters.computeIfAbsent("instrumentId", id -> RandomStringUtils.secure().nextAlphanumeric(4));
        priceJpaRepository.deleteById(instrumentId);
        return parameters;
    }

    /**
     * Cleans up after the "price can be saved" provider state.
     * <p>
     * Очищает после состояния поставщика "цена может быть сохранена".
     *
     * @param parameters The state parameters from setup
     */
    @State(value = "price can be saved", action = StateChangeAction.TEARDOWN)
    @Transactional
    public void priceCanBeSavedCleanup(Map<String, String> parameters) {
        priceWithIdExistsCleanup(parameters);
    }
}
