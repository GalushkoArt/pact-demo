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
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

/**
 * Provider verification for protobuf Kafka price update messages.
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
    @PactVerifyProvider("proto price updated")
    public MessageAndMetadata verifyPriceUpdatedMessage() {
        Price priceMsg = Price.newBuilder()
                .setInstrumentId(randomAlphabetic(4))
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
