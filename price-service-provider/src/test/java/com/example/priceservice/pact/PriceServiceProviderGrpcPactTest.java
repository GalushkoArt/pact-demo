package com.example.priceservice.pact;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junit5.PluginTestTarget;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.StateChangeAction;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.junitsupport.loader.PactBrokerAuth;
import au.com.dius.pact.provider.junitsupport.loader.VersionSelector;
import au.com.dius.pact.provider.junitsupport.loader.PactBrokerConsumerVersionSelectors;
import au.com.dius.pact.provider.junitsupport.loader.SelectorBuilder;
import com.example.priceservice.adapter.persistence.entity.PriceEntity;
import com.example.priceservice.adapter.persistence.repository.PriceJpaRepository;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "grpc.server.port=9090",
        "admin.username=admin",
        "admin.password=password"
})
@Provider("price-service-provider-grpc")
@PactBroker(
        url = "${PACT_URL:http://localhost:9292}",
        authentication = @PactBrokerAuth(username = "${PACT_BROKER_USER:pact}", password = "${PACT_BROKER_PASSWORD:pact}"),
        providerBranch = "${GIT_BRANCH:master}"
)
@VersionSelector
public class PriceServiceProviderGrpcPactTest {
    private static final Logger log = LoggerFactory.getLogger(PriceServiceProviderGrpcPactTest.class);

    @Autowired
    private PriceJpaRepository priceJpaRepository;

    static ReentrantLock pricesExist = new ReentrantLock();

    @BeforeEach
    void setUp(PactVerificationContext context) {
        context.setTarget(new PluginTestTarget(Map.of(
                "host", "localhost",
                "port", 9090,
                "transport", "grpc"
        )));
    }

    @PactBrokerConsumerVersionSelectors
    public static SelectorBuilder consumerVersionSelectors() {
        return new SelectorBuilder()
                .mainBranch()
                .latestTag("dev");
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void testTemplate(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State(value = "prices exist", action = StateChangeAction.SETUP)
    @Transactional
    public void pricesExist() throws InterruptedException {
        if (pricesExist.tryLock(30, TimeUnit.SECONDS)) {
            try {
                priceJpaRepository.findById("AAPL").ifPresentOrElse(
                        e -> {},
                        () -> priceJpaRepository.save(PriceEntity.builder()
                                .instrumentId("AAPL")
                                .bidPrice(new BigDecimal("175.50"))
                                .askPrice(new BigDecimal("175.75"))
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
        var parameters = new HashMap<String, String>();
        var instrumentId = parameters.computeIfAbsent("instrumentId", id -> RandomStringUtils.secure().nextAlphanumeric(4));
        priceJpaRepository.findById(instrumentId).ifPresent(price -> priceJpaRepository.delete(price));
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
        Optional.ofNullable(parameters.get("instrumentId")).ifPresent(id -> priceJpaRepository.deleteById(id));
    }

    @State("price with ID UNKNOWN does not exist")
    @Transactional
    public void priceWithIdUnknownDoesNotExist() {
        priceJpaRepository.findById("UNKNOWN").ifPresent(price -> priceJpaRepository.delete(price));
    }
}
