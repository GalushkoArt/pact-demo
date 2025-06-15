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
import com.example.priceservice.util.TestDataFactory;
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

/**
 * Provider side verification for Kafka price update messages.
 * Demonstrates asynchronous message contract testing.
 * <p>
 * Проверка контрактов Kafka на стороне поставщика.
 * Показывает тестирование асинхронных сообщений.
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

    /**
     * Selects consumer versions to verify.
     * Keeping the provider in sync with consumer branches is recommended.
     * <p>
     * Выбор версий потребителей для проверки.
     * Позволяет держать поставщика синхронным с ветками потребителей.
     */
    @PactBrokerConsumerVersionSelectors
    public static SelectorBuilder consumerVersionSelectors() {
        return new SelectorBuilder()
                .mainBranch()
                .latestTag("dev");
    }

    /**
     * Template method invoked for each Pact interaction.
     * <p>
     * Шаблонный метод, вызываемый для каждой Pact-итерации.
     */
    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void testTemplate(Pact pact, Interaction interaction, PactVerificationContext context) {
        context.verifyInteraction();
    }

    /**
     * Configure the message test target before verification.
     * <p>
     * Настраивает MessageTestTarget перед проверкой.
     */
    @BeforeEach
    void before(PactVerificationContext context) {
        context.setTarget(new MessageTestTarget());
    }

    /**
     * Provider state for an existing price update event.
     * Keeping state setup minimal reduces test fragility.
     * <p>
     * Состояние поставщика для события обновления цены.
     * Минимальная подготовка состояния снижает хрупкость тестов.
     */
    @State(value = "price update event", action = StateChangeAction.SETUP)
    public Map<String, String> priceUpdateExists() {
        return new HashMap<>();
    }

    //Try not to duplicate provider name across providers
    /**
     * Creates the message that is published by the provider.
     * <p>
     * Создает сообщение, публикуемое поставщиком.
     */
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
