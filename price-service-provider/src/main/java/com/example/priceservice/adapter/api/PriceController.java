package com.example.priceservice.adapter.api;

import com.example.priceservice.adapter.api.model.PriceDto;
import com.example.priceservice.domain.model.Price;
import com.example.priceservice.domain.service.PriceServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for price-related operations.
 * This adapter exposes the domain service as a REST API.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class PriceController implements PricesApi {

    private final PriceServiceImpl priceService;
    private final PriceMapper priceMapper;

    @Override
    public ResponseEntity<List<PriceDto>> getAllPrices() {
        log.info("REST request to get all prices");
        List<Price> prices = priceService.getAllPrices();
        List<PriceDto> priceDtos = prices.stream()
                .map(priceMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(priceDtos);
    }

    @Override
    public ResponseEntity<PriceDto> getPrice(String instrumentId) {
        log.info("REST request to get price for instrument: {}", instrumentId);
        return priceService.getPrice(instrumentId)
                .map(priceMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<PriceDto> savePrice(String instrumentId, PriceDto priceDto) {
        log.info("REST request to save price for instrument: {}", instrumentId);

        // Ensure the path variable matches the DTO
        if (!instrumentId.equals(priceDto.getInstrumentId())) {
            priceDto.setInstrumentId(instrumentId);
        }

        Price price = priceMapper.toEntity(priceDto);
        Price savedPrice = priceService.savePrice(price);
        return ResponseEntity.ok(priceMapper.toDto(savedPrice));
    }

    @Override
    public ResponseEntity<Void> deletePrice(String instrumentId) {
        log.info("REST request to delete price for instrument: {}", instrumentId);
        boolean deleted = priceService.deletePrice(instrumentId);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
