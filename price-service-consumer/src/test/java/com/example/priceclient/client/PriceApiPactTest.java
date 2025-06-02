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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
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
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "password";
    private static final String AUTH_HEADER = "Basic " + Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes());

    @BeforeAll
    public static void setup() {
        System.setProperty("http.keepAlive", "false");
    }

    @Pact(consumer = "price-service-consumer")
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

    @Pact(consumer = "price-service-consumer")
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

    @Pact(consumer = "price-service-consumer")
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

    @Pact(consumer = "price-service-consumer")
    public RequestResponsePact savePricePact(PactDslWithProvider builder) {
        var timestamp = Instant.now().toString();

        return builder
                .given("price can be saved")
                .uponReceiving("an authenticated request to save price for AAPL")
                .pathFromProviderState("/prices/${instrumentId}", "/prices/AAPL")
                .method("POST")
                .headers("Authorization", AUTH_HEADER)
                .headers("Content-Type", "application/json")
                .body(newJsonBody(body -> {
                    body.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
                    body.decimalType("bidPrice", 176.50);
                    body.decimalType("askPrice", 176.75);
                    body.stringType("lastUpdated", timestamp);
                }).build())
                .willRespondWith()
                .status(200)
                .body(newJsonBody(body -> {
                    body.stringType("instrumentId", "AAPL");
                    body.decimalType("bidPrice", 176.50);
                    body.decimalType("askPrice", 176.75);
                    body.stringType("lastUpdated", timestamp);
                }).build())
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "savePricePact")
    void testSavePrice() {
        PriceDto priceDto = new PriceDto();
        priceDto.setInstrumentId("AAPL");
        priceDto.setBidPrice(new BigDecimal("176.50"));
        priceDto.setAskPrice(new BigDecimal("176.75"));
        priceDto.setLastUpdated(OffsetDateTime.now());

        PriceDto savedPrice = pricesApi.savePrice(priceDto.getInstrumentId(), priceDto);

        assertThat(savedPrice.getInstrumentId()).isEqualTo("AAPL");
        assertThat(savedPrice.getBidPrice()).isEqualByComparingTo(new BigDecimal("176.50"));
        assertThat(savedPrice.getAskPrice()).isEqualByComparingTo(new BigDecimal("176.75"));
    }

    @Pact(consumer = "price-service-consumer")
    public RequestResponsePact saveWithWrongAuthPact(PactDslWithProvider builder) {
        var timestamp = Instant.now().toString();

        return builder
                .given("price can be saved")
                .uponReceiving("an unauthenticated request to save price")
                .pathFromProviderState("/prices/${instrumentId}", "/prices/AAPL")
                .method("POST")
                .headers("Content-Type", "application/json")
                .body(newJsonBody(body -> {
                    body.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
                    body.decimalType("bidPrice", 176.50);
                    body.decimalType("askPrice", 176.75);
                    body.stringType("lastUpdated", timestamp);
                }).build())
                .willRespondWith()
                .status(401)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "saveWithWrongAuthPact")
    void testSavePriceWithoutAuth() {
        PriceDto priceDto = new PriceDto();
        priceDto.setInstrumentId("AAPL");
        priceDto.setBidPrice(new BigDecimal("176.50"));
        priceDto.setAskPrice(new BigDecimal("176.75"));
        priceDto.setLastUpdated(OffsetDateTime.now());

        assertThatThrownBy(() -> pricesApi.savePrice(priceDto.getInstrumentId(), priceDto))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    @Pact(consumer = "price-service-consumer")
    public RequestResponsePact deletePricePact(PactDslWithProvider builder) {
        return builder
                .given("price with ID exists")
                .uponReceiving("an authenticated request to delete price with ID AAPL")
                .pathFromProviderState("/prices/${instrumentId}", "/prices/AAPL")
                .method("DELETE")
                .headers("Authorization", AUTH_HEADER)
                .willRespondWith(response -> response.status(204))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "deletePricePact")
    void testDeletePrice() {
        pricesApi.deletePriceWithResponseSpec("AAPL").toBodilessEntity();
    }

    @Pact(consumer = "price-service-consumer")
    public RequestResponsePact deleteWithWrongAuthPact(PactDslWithProvider builder) {
        return builder
                .given("price with ID exists")
                .uponReceiving("an unauthenticated request to delete price")
                .pathFromProviderState("/prices/${instrumentId}", "/prices/AAPL")
                .method("DELETE")
                .willRespondWith()
                .status(401)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "deleteWithWrongAuthPact")
    void testDeletePriceWithoutAuth() {
        assertThatThrownBy(() -> pricesApi.deletePrice("AAPL"))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }
}
