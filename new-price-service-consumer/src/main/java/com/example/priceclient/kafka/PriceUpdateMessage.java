package com.example.priceclient.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PriceUpdateMessage {
    private String instrumentId;
    private BigDecimal bidPrice;
    private BigDecimal askPrice;
    private Instant lastUpdated;
}
