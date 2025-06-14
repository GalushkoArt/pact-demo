package com.example.priceservice.pact;

import au.com.dius.pact.core.model.Interaction;
import au.com.dius.pact.core.model.Pact;
import au.com.dius.pact.provider.MessageAndMetadata;
import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit5.MessageTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.StateChangeAction;
import au.com.dius.pact.provider.junitsupport.loader.*;
import com.example.priceservice.config.JacksonConfig;
import com.example.priceservice.domain.model.Price;
import com.example.priceservice.domain.model.PriceUpdateMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.example.priceservice.util.TestDataFactory;

/**
 * Provider side verification for Kafka price update messages.
 */
@Provider("price-service-provider-kafka")
@PactBroker(
        url = "${PACT_URL:http://localhost:9292}",
        authentication = @PactBrokerAuth(username = "${PACT_BROKER_USER:pact}", password = "${PACT_BROKER_PASSWORD:pact}"),
        providerBranch = "${GIT_BRANCH:master}"
)
@VersionSelector
@SpringBootTest(classes = {
        JacksonConfig.class,
        JacksonAutoConfiguration.class,
})
public class PriceServiceProviderKafkaPactTest {
    @Autowired
    private ObjectMapper mapper;

    @PactBrokerConsumerVersionSelectors
    public static SelectorBuilder consumerVersionSelectors() {
        return new SelectorBuilder()
                .mainBranch()
                .latestTag("dev");
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void testTemplate(Pact pact, Interaction interaction, PactVerificationContext context) {
        context.verifyInteraction();
    }

    @BeforeEach
    void before(PactVerificationContext context) {
        context.setTarget(new MessageTestTarget());
    }

    @State(value = "price update event", action = StateChangeAction.SETUP)
    public Map<String, String> priceUpdateExists() {
        return new HashMap<>();
    }

    //Try not to duplicate provider name across providers
    @PactVerifyProvider("price updated")
    public MessageAndMetadata verifyPriceUpdatedMessage() throws JsonProcessingException {
        // Build a representative domain object and convert it to the Kafka message
        // Создаем доменный объект и конвертируем его в Kafka-сообщение
        Price price = Price.builder()
                .instrumentId(TestDataFactory.randomInstrumentId())
                .bidPrice(new BigDecimal("10"))
                .askPrice(new BigDecimal("11"))
                .lastUpdated(Instant.now())
                .build();
        PriceUpdateMessage message = PriceUpdateMessage.fromDomain(price);
        var metadata = new HashMap<String, Object>();
        metadata.put("contentType", "application/json");
        return new MessageAndMetadata(mapper.writeValueAsBytes(message), metadata);
    }
}
