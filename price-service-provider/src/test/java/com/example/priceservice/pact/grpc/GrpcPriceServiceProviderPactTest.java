package com.example.priceservice.pact.grpc;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junit5.PluginTestTarget;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.StateChangeAction;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.example.priceservice.adapter.persistence.entity.PriceEntity;
import com.example.priceservice.adapter.persistence.repository.PriceJpaRepository;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"grpc.server.port=9091", "admin.username=admin", "admin.password=password"})
@ExtendWith(SpringExtension.class)
@Provider("price-service-provider-grpc")
@PactFolder("../new-price-service-consumer/build/pacts")
public class GrpcPriceServiceProviderPactTest {

    @Autowired
    private PriceJpaRepository priceJpaRepository;

    @BeforeEach
    void setUp(PactVerificationContext context) {
        context.setTarget(new PluginTestTarget(Map.of(
                "host", "localhost",
                "port", 9091,
                "transport", "grpc"
        )));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verify(PactVerificationContext context) {
        context.verifyInteraction();
    }

    static ReentrantLock pricesExist = new ReentrantLock();

    @State(value = "prices exist", action = StateChangeAction.SETUP)
    @Transactional
    public void pricesExist() throws InterruptedException {
        if (pricesExist.tryLock(30, TimeUnit.SECONDS)) {
            try {
                priceJpaRepository.findById("AAPL").ifPresentOrElse(e -> {},
                        () -> priceJpaRepository.save(PriceEntity.builder()
                                .instrumentId("AAPL")
                                .bidPrice(new BigDecimal("175.50"))
                                .askPrice(new BigDecimal("175.75"))
                                .lastUpdated(Instant.now())
                                .build()));
            } finally {
                pricesExist.unlock();
            }
        }
    }

    @State(value = "price with ID exists", action = StateChangeAction.SETUP)
    @Transactional
    public Map<String, String> priceWithIdExists() {
        var parameters = new HashMap<String, String>();
        var instrumentId = parameters.computeIfAbsent("instrumentId", id -> RandomStringUtils.randomAlphabetic(4));
        priceJpaRepository.findById(instrumentId).ifPresent(priceJpaRepository::delete);

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
        Optional.ofNullable(parameters.get("instrumentId")).ifPresent(priceJpaRepository::deleteById);
    }

    @State("price with ID UNKNOWN does not exist")
    @Transactional
    public void priceUnknownDoesNotExist() {
        priceJpaRepository.findById("UNKNOWN").ifPresent(priceJpaRepository::delete);
    }
}
