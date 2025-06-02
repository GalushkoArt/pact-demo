package com.example.priceclient.client;

import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit.MockServerConfig;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.example.priceservice.client.api.OrderBookApi;
import com.example.priceservice.client.api.model.OrderBookDto;
import com.example.priceservice.client.api.model.OrderDto;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pact consumer test for the Order Book API.
 * This test defines the contract between the consumer and provider for order book operations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "price-service.base-url=http://localhost:9091",
        "price-service.username=admin",
        "price-service.password=password",
})
@MockServerConfig(port = "9091")
@ExtendWith(PactConsumerTestExt.class)
@Execution(ExecutionMode.SAME_THREAD)
@PactTestFor(providerName = "price-service-provider-orderbook")
public class OrderBookApiPactTest {
    @Autowired
    private OrderBookApi orderBookApi;
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "password";
    private static final String AUTH_HEADER = "Basic " + Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes());

    @BeforeAll
    public static void setup() {
        System.setProperty("http.keepAlive", "false");
    }

    @Pact(consumer = "price-service-consumer")
    public RequestResponsePact getOrderBookPact(PactDslWithProvider builder) {
        var timestamp = Instant.now().toString();
        return builder
                .given("order book with ID exists")
                .uponReceiving("a request for order book with ID AAPL")
                .pathFromProviderState("/orderbook/${instrumentId}", "/orderbook/AAPL")
                .method("GET")
                .willRespondWith()
                .status(200)
                .body(newJsonBody(o -> {
                    o.stringType("lastUpdated", timestamp);
                    o.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
                    o.eachLike("bidOrders", 2, bid -> {
                        bid.numberType("price", 175.50);
                        bid.numberType("volume", 100.0);
                    });
                    o.eachLike("askOrders", 2, ask -> {
                        ask.numberType("price", 175.75);
                        ask.numberType("volume", 150.0);
                    });
                }).build())
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getOrderBookPact")
    void testGetOrderBook() {
        var orderBook = orderBookApi.getOrderBook("AAPL");

        assertThat(orderBook.getInstrumentId()).isEqualTo("AAPL");
        assertThat(orderBook.getBidOrders()).hasSize(2);
        assertThat(orderBook.getAskOrders()).hasSize(2);
        assertThat(orderBook.getLastUpdated()).isNotNull();
    }

    @Pact(consumer = "price-service-consumer")
    public RequestResponsePact getOrderBookNotFoundPact(PactDslWithProvider builder) {
        return builder
                .given("order book with ID UNKNOWN does not exist")
                .uponReceiving("a request for order book with ID UNKNOWN")
                .path("/orderbook/UNKNOWN")
                .method("GET")
                .willRespondWith()
                .status(404)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getOrderBookNotFoundPact")
    void testGetOrderBookNotFound() {
        assertThatThrownBy(() -> orderBookApi.getOrderBook("UNKNOWN"))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Pact(consumer = "price-service-consumer")
    public RequestResponsePact saveOrderBookPact(PactDslWithProvider builder) {
        var timestamp = Instant.now().toString();
        return builder
                .given("order book can be saved")
                .uponReceiving("an authenticated request to save order book for AAPL")
                .pathFromProviderState("/orderbook/${instrumentId}", "/orderbook/AAPL")
                .method("POST")
                .headers("Content-Type", "application/json")
                .headers("Authorization", AUTH_HEADER)
                .body(newJsonBody(o -> {
                    o.stringType("lastUpdated", timestamp);
                    o.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
                    o.eachLike("bidOrders", 2, bid -> {
                        bid.numberType("price", 176.50);
                        bid.numberType("volume", 120.0);
                    });
                    o.eachLike("askOrders", 2, ask -> {
                        ask.numberType("price", 176.75);
                        ask.numberType("volume", 170.0);
                    });
                }).build())
                .willRespondWith()
                .status(200)
                .body(newJsonBody(o -> {
                    o.stringType("lastUpdated", timestamp);
                    o.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
                    o.eachLike("bidOrders", 2, bid -> {
                        bid.numberType("price", 176.50);
                        bid.numberType("volume", 120.0);
                    });
                    o.eachLike("askOrders", 2, ask -> {
                        ask.numberType("price", 176.75);
                        ask.numberType("volume", 170.0);
                    });
                }).build())
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "saveOrderBookPact")
    void testSaveOrderBook() {
        OrderBookDto orderBookDto = new OrderBookDto();
        orderBookDto.setInstrumentId("AAPL");

        List<OrderDto> bidOrders = new ArrayList<>();
        bidOrders.add(createOrderDto(new BigDecimal("176.50"), new BigDecimal("120.0")));
        bidOrders.add(createOrderDto(new BigDecimal("176.45"), new BigDecimal("220.0")));
        orderBookDto.setBidOrders(bidOrders);

        List<OrderDto> askOrders = new ArrayList<>();
        askOrders.add(createOrderDto(new BigDecimal("176.75"), new BigDecimal("170.0")));
        askOrders.add(createOrderDto(new BigDecimal("176.80"), new BigDecimal("270.0")));
        orderBookDto.setAskOrders(askOrders);

        orderBookDto.setLastUpdated(OffsetDateTime.now());

        var savedOrderBook = orderBookApi.saveOrderBook(orderBookDto.getInstrumentId(), orderBookDto);

        assertThat(savedOrderBook.getInstrumentId()).isEqualTo("AAPL");
        assertThat(savedOrderBook.getBidOrders()).hasSize(2);
        assertThat(savedOrderBook.getAskOrders()).hasSize(2);
    }

    @Pact(consumer = "price-service-consumer")
    public RequestResponsePact saveOrderBookWithWrongAuthPact(PactDslWithProvider builder) {
        var timestamp = Instant.now().toString();
        return builder
                .given("order book can be saved")
                .uponReceiving("an unauthenticated request to save order book")
                .pathFromProviderState("/orderbook/${instrumentId}", "/orderbook/AAPL")
                .method("POST")
                .headers("Content-Type", "application/json")
                .body(newJsonBody(o -> {
                    o.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
                    o.stringType("lastUpdated", timestamp);
                    o.eachLike("bidOrders", 2, bid -> {
                        bid.numberType("price", 176.50);
                        bid.numberType("volume", 120.0);
                    });
                    o.eachLike("askOrders", 2, bid -> {
                        bid.numberType("price", 176.75);
                        bid.numberType("volume", 170.0);
                    });
                }).build())
                .willRespondWith()
                .status(401)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "saveOrderBookWithWrongAuthPact")
    void testSaveOrderBookWithoutAuth() {
        OrderBookDto orderBookDto = new OrderBookDto();
        orderBookDto.setInstrumentId("AAPL");

        List<OrderDto> bidOrders = new ArrayList<>();
        bidOrders.add(createOrderDto(new BigDecimal("176.50"), new BigDecimal("120.0")));
        orderBookDto.setBidOrders(bidOrders);

        List<OrderDto> askOrders = new ArrayList<>();
        askOrders.add(createOrderDto(new BigDecimal("176.75"), new BigDecimal("170.0")));
        orderBookDto.setAskOrders(askOrders);

        orderBookDto.setLastUpdated(OffsetDateTime.now());

        assertThatThrownBy(() -> orderBookApi.saveOrderBook(orderBookDto.getInstrumentId(), orderBookDto))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    private OrderDto createOrderDto(BigDecimal price, BigDecimal volume) {
        OrderDto orderDto = new OrderDto();
        orderDto.setPrice(price);
        orderDto.setVolume(volume);
        return orderDto;
    }
}
