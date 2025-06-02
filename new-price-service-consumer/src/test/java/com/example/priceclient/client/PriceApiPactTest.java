package com.example.priceclient.client;

import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit.MockServerConfig;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.example.priceservice.client.api.PricesApi;
import com.example.priceservice.client.api.model.PriceDto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.util.List;

import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonArrayMinLike;
import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pact consumer test for the Price Service client.
 * This test defines the contract between the consumer and provider for price-related operations.
 * Updated to include authentication for DELETE and POST methods.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "price-service.base-url=http://localhost:9090",
        "price-service.username=admin",
        "price-service.password=password",
})
@MockServerConfig(port = "9090")
@ExtendWith(PactConsumerTestExt.class)
@Execution(ExecutionMode.SAME_THREAD)
@PactTestFor(providerName = "price-service-provider-price")
public class PriceApiPactTest {
    @Autowired
    private PricesApi pricesApi;

    @BeforeAll
    public static void setup() {
        System.setProperty("http.keepAlive", "false");
    }

    @Pact(consumer = "new-price-service-consumer")
    public RequestResponsePact getAllPricesPact(PactDslWithProvider builder) {
        var timestamp = Instant.now().toString();
        return builder
                .given("prices exist")
                .uponReceiving("a request for all prices")
                .path("/prices")
                .method("GET")
                .willRespondWith()
                .status(200)
                .body(newJsonArrayMinLike(1, array -> {
                    array.object(o -> {
                        o.stringType("instrumentId", "AAPL");
                        o.decimalType("bidPrice", 175.50);
                        o.decimalType("askPrice", 175.75);
                        o.stringType("lastUpdated", timestamp);
                    });
                }).build())
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getAllPricesPact")
    void testGetAllPrices() {
        List<PriceDto> prices = pricesApi.getAllPrices();

        assertThat(prices).isNotNull();
        assertThat(prices.size()).isGreaterThanOrEqualTo(1);
        assertThat(prices.get(0).getInstrumentId()).isNotNull();
        assertThat(prices.get(0).getBidPrice()).isNotNull();
        assertThat(prices.get(0).getAskPrice()).isNotNull();
        assertThat(prices.get(0).getLastUpdated()).isNotNull();
    }

    @Pact(consumer = "new-price-service-consumer")
    public RequestResponsePact getPricePact(PactDslWithProvider builder) {
        var timestamp = Instant.now().toString();
        return builder
                .given("price with ID exists")
                .uponReceiving("a request for price with ID AAPL")
                .pathFromProviderState("/prices/${instrumentId}", "/prices/AAPL")
                .method("GET")
                .willRespondWith()
                .status(200)
                .body(newJsonBody(body -> {
                    body.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
                    body.decimalType("bidPrice", 175.50);
                    body.decimalType("askPrice", 175.75);
                    body.stringType("lastUpdated", timestamp);
                }).build())
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getPricePact")
    void testGetPrice() {
        PriceDto price = pricesApi.getPrice("AAPL");

        assertThat(price.getInstrumentId()).isEqualTo("AAPL");
        assertThat(price.getBidPrice()).isNotNull();
        assertThat(price.getAskPrice()).isNotNull();
        assertThat(price.getLastUpdated()).isNotNull();
    }

    @Pact(consumer = "new-price-service-consumer")
    public RequestResponsePact getPriceNotFoundPact(PactDslWithProvider builder) {
        return builder
                .given("price with ID UNKNOWN does not exist")
                .uponReceiving("a request for price with ID UNKNOWN")
                .path("/prices/UNKNOWN")
                .method("GET")
                .willRespondWith()
                .status(404)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getPriceNotFoundPact")
    void testGetPriceNotFound() {
        assertThatThrownBy(() -> pricesApi.getPrice("UNKNOWN"))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }
}
