package com.example.priceservice.adapter.api;

import com.example.priceservice.adapter.api.model.PriceDto;
import com.example.priceservice.domain.model.Price;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

/**
 * Mapper for converting between Price domain model and PriceDto.
 * This adapter handles the translation between domain and API models.
 */
@Component
public class PriceMapper {

    /**
     * Converts a domain Price to a PriceDto
     *
     * @param price the domain model
     * @return the DTO
     */
    public PriceDto toDto(Price price) {
        if (price == null) {
            return null;
        }
        
        return PriceDto.builder()
                .instrumentId(price.getInstrumentId())
                .bidPrice(price.getBidPrice())
                .askPrice(price.getAskPrice())
                .lastUpdated(price.getLastUpdated().atOffset(ZoneOffset.UTC))
                .build();
    }

    /**
     * Converts a PriceDto to a domain Price
     *
     * @param priceDto the DTO
     * @return the domain model
     */
    public Price toEntity(PriceDto priceDto) {
        if (priceDto == null) {
            return null;
        }
        
        return Price.builder()
                .instrumentId(priceDto.getInstrumentId())
                .bidPrice(priceDto.getBidPrice())
                .askPrice(priceDto.getAskPrice())
                .lastUpdated(priceDto.getLastUpdated().toInstant())
                .build();
    }
}
