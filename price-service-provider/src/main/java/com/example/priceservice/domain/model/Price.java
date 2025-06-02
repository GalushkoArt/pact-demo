package com.example.priceservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain model representing the price of a financial instrument.
 * This is the internal representation used within the provider service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Price {
    private String instrumentId;
    private BigDecimal bidPrice;
    private BigDecimal askPrice;
    private Instant lastUpdated;
}
