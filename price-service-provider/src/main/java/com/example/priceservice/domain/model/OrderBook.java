package com.example.priceservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Domain model representing an order book for a financial instrument.
 * Contains bid and ask orders with prices and volumes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderBook {
    private String instrumentId;
    private List<Order> bidOrders;
    private List<Order> askOrders;
    private Instant lastUpdated;

    /**
     * Represents a single order in the order book
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Order {
        private BigDecimal price;
        private BigDecimal volume;
    }
}
