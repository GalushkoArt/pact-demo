package com.example.priceservice.adapter.api;

import com.example.priceservice.adapter.api.model.OrderBookDto;
import com.example.priceservice.domain.model.OrderBook;
import com.example.priceservice.domain.service.PriceServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for order book operations.
 * This adapter exposes the domain service as a REST API.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class OrderBookController implements OrderBookApi {

    private final PriceServiceImpl priceService;
    private final OrderBookMapper orderBookMapper;

    @Override
    public ResponseEntity<OrderBookDto> getOrderBook(String instrumentId) {
        log.info("REST request to get order book for instrument: {}", instrumentId);
        return priceService.getOrderBook(instrumentId)
                .map(orderBookMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<OrderBookDto> saveOrderBook(String instrumentId, OrderBookDto orderBookDto) {
        log.info("REST request to save order book for instrument: {}", instrumentId);

        // Ensure the path variable matches the DTO
        if (!instrumentId.equals(orderBookDto.getInstrumentId())) {
            orderBookDto.setInstrumentId(instrumentId);
        }

        OrderBook orderBook = orderBookMapper.toEntity(orderBookDto);
        OrderBook savedOrderBook = priceService.saveOrderBook(orderBook);
        return ResponseEntity.ok(orderBookMapper.toDto(savedOrderBook));
    }
}
