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
 * Provider-side contract verification test.
 * This test verifies that the provider can fulfill the contracts defined by consumers.
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
    private static final String username = "admin";
    private static final String password = "password";
    private static final String AUTH_HEADER = "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());

    @PactBrokerConsumerVersionSelectors
    public static SelectorBuilder consumerVersionSelectors() {
        return new SelectorBuilder()
                .mainBranch()
                .tag("mobile")
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

    @State(value = "order book with ID exists", action = StateChangeAction.SETUP)
    @Transactional
    public Map<String, String> orderBookWithIdExists() {
        // Clear existing data for this ID
        var parameters = new HashMap<String, String>();
        var instrumentId = parameters.computeIfAbsent("instrumentId", id -> RandomStringUtils.secure().nextAlphanumeric(4));
        orderBookJpaRepository.findById(instrumentId).ifPresent(orderBook -> orderBookJpaRepository.delete(orderBook));

        // Create test data
        OrderBookEntity orderBook = OrderBookEntity.builder()
                .instrumentId(instrumentId)
                .lastUpdated(Instant.now())
                .build();

        // Create bid orders
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

    @State(value = "order book with ID exists", action = StateChangeAction.TEARDOWN)
    @Transactional
    public void orderBookWithIdExistsCleanup(Map<String, String> parameters) {
        log.debug("Cleaning up order book with ID: {}", parameters.get("instrumentId"));
        Optional.ofNullable(parameters.get("instrumentId")).ifPresent(id -> orderBookJpaRepository.deleteById(id));
    }

    @State(value = "order book can be saved", action = StateChangeAction.SETUP)
    @Transactional
    public Map<String, String> orderBookCanBeSaved() {
        // Clear existing data for this ID
        var parameters = new HashMap<String, String>();
        var instrumentId = parameters.computeIfAbsent("instrumentId", id -> RandomStringUtils.secure().nextAlphanumeric(4));
        orderBookJpaRepository.findById(instrumentId).ifPresent(orderBook -> orderBookJpaRepository.delete(orderBook));
        return parameters;
    }

    @State("order book with ID UNKNOWN does not exist")
    @Transactional
    public void orderBookWithIdUnknownDoesNotExist() {
        // Ensure the order book doesn't exist
        orderBookJpaRepository.findById("UNKNOWN").ifPresent(orderBook -> orderBookJpaRepository.delete(orderBook));
    }
}
