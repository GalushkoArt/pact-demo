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
    private static final String username = "admin";
    private static final String password = "password";
    private static final String AUTH_HEADER = "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());

    @PactBrokerConsumerVersionSelectors
    public static SelectorBuilder consumerVersionSelectors() {
        return new SelectorBuilder()
                .mainBranch()
                .tag("prod")
                .latestTag("dev");
    }

    @BeforeEach
    void setUp(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", port));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void testTemplate(PactVerificationContext context, HttpRequest request) {
        replaceAuthHeader(request);
        context.verifyInteraction();
    }

    private void replaceAuthHeader(HttpRequest request) {
        if (request.containsHeader("Authorization")) {
            request.removeHeaders("Authorization");
            request.addHeader("Authorization", AUTH_HEADER);
        } else {
            log.warn("Request does not contain Authorization header. Skipping authorization header replacement.");
        }
    }

    static ReentrantLock pricesExist = new ReentrantLock();

    @State(value = "prices exist", action = StateChangeAction.SETUP)
    @Transactional
    public void pricesExist() throws InterruptedException {
        // Create test data
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

    @State(value = "price with ID exists", action = StateChangeAction.SETUP)
    @Transactional
    public Map<String, String> priceWithIdExists() {
        // Clear existing data for this ID
        var parameters = new HashMap<String, String>();
        var instrumentId = parameters.computeIfAbsent("instrumentId", id -> RandomStringUtils.secure().nextAlphanumeric(4));
        priceJpaRepository.findById(instrumentId).ifPresent(price -> priceJpaRepository.delete(price));

        // Create test data
        PriceEntity apple = PriceEntity.builder()
                .instrumentId(instrumentId)
                .bidPrice(new BigDecimal("175.50"))
                .askPrice(new BigDecimal("175.75"))
                .lastUpdated(Instant.now())
                .build();

        priceJpaRepository.save(apple);
        return parameters;
    }

    @State(value = "price with ID exists", action = StateChangeAction.TEARDOWN)
    @Transactional
    public void priceWithIdExistsCleanup(Map<String, String> parameters) {
        log.debug("Cleaning up price with ID: {}", parameters.get("instrumentId"));
        Optional.ofNullable(parameters.get("instrumentId")).ifPresent(id -> priceJpaRepository.deleteById(id));
    }

    @State("price with ID UNKNOWN does not exist")
    @Transactional
    public void priceWithIdUnknownDoesNotExist() {
        // Ensure the price doesn't exist
        priceJpaRepository.findById("UNKNOWN").ifPresent(price -> priceJpaRepository.delete(price));
    }

    @State(value = "price can be saved", action = StateChangeAction.SETUP)
    @Transactional
    public Map<String, String> priceCanBeSaved() {
        // No specific setup needed as the repository allows saving
        var parameters = new HashMap<String, String>();
        var instrumentId = parameters.computeIfAbsent("instrumentId", id -> RandomStringUtils.secure().nextAlphanumeric(4));
        priceJpaRepository.deleteById(instrumentId);
        return parameters;
    }

    @State(value = "price can be saved", action = StateChangeAction.TEARDOWN)
    @Transactional
    public void priceCanBeSavedCleanup(Map<String, String> parameters) {
        priceWithIdExistsCleanup(parameters);
    }
}
