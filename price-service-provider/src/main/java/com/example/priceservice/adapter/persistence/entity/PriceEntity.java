package com.example.priceservice.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity for storing price information in the database.
 */
@Entity
@Table(name = "prices")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceEntity {

    @Id
    @Column(name = "instrument_id")
    private String instrumentId;

    @Column(name = "bid_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal bidPrice;

    @Column(name = "ask_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal askPrice;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;
}
