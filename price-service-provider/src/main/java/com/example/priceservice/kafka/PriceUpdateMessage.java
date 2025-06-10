package com.example.priceservice.kafka;

import com.example.priceservice.domain.model.Price;
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

    public static PriceUpdateMessage fromDomain(Price price) {
        return PriceUpdateMessage.builder()
                .instrumentId(price.getInstrumentId())
                .bidPrice(price.getBidPrice())
                .askPrice(price.getAskPrice())
                .lastUpdated(price.getLastUpdated())
                .build();
    }
}
