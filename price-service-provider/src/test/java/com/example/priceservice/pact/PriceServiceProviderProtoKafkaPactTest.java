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
import com.example.priceservice.grpc.Price;
import com.example.priceservice.grpc.PriceUpdate;
import com.example.priceservice.grpc.UpdateType;
import com.example.priceservice.util.TestDataFactory;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

/**
 * Provider verification for protobuf Kafka price update messages.
 * Demonstrates the use of the Pact protobuf plugin.
 * <p>
 * Проверка сообщений Kafka в формате protobuf.
 * Показывает использование плагина Pact protobuf.
 */
@Provider("price-service-provider-kafka-proto")
@PactBroker(
        url = "${PACT_URL:http://localhost:9292}",
        authentication = @PactBrokerAuth(username = "${PACT_BROKER_USER:pact}", password = "${PACT_BROKER_PASSWORD:pact}"),
        providerBranch = "${GIT_BRANCH:master}"
)
@VersionSelector
@SpringBootTest
public class PriceServiceProviderProtoKafkaPactTest {

    /**
     * Selects the consumer versions to verify.
     * <p>
     * Выбор версий потребителей для проверки.
     */
    @PactBrokerConsumerVersionSelectors
    public static SelectorBuilder consumerVersionSelectors() {
        return new SelectorBuilder()
                .mainBranch()
                .latestTag("dev");
    }

    /**
     * Template method to trigger verification of each interaction.
     * <p>
     * Шаблонный метод для проверки каждого взаимодействия.
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
     * Provider state for an existing protobuf price update.
     * <p>
     * Состояние поставщика для существующего события обновления цены в protobuf.
     */
    @State(value = "price update event", action = StateChangeAction.SETUP)
    public Map<String, String> priceUpdateExists() {
        return new HashMap<>();
    }

    //Try not to duplicate provider name across providers
    /**
     * Builds the protobuf message published by the provider.
     * <p>
     * Создает protobuf-сообщение, публикуемое поставщиком.
     */
    @PactVerifyProvider("proto price updated")
    public MessageAndMetadata verifyPriceUpdatedMessage() {
        Price priceMsg = Price.newBuilder()
                .setInstrumentId(TestDataFactory.randomInstrumentId())
                .setBidPrice(10)
                .setAskPrice(11)
                .setLastUpdated(Timestamp.getDefaultInstance())
                .build();
        PriceUpdate message = PriceUpdate.newBuilder()
                .setPrice(priceMsg)
                .setUpdateType(UpdateType.UPDATED)
                .build();
        var metadata = new HashMap<String, Object>();
        metadata.put("contentType", "application/protobuf;message=." + PriceUpdate.getDescriptor().getFullName());
        return new MessageAndMetadata(message.toByteArray(), metadata);
    }
}
