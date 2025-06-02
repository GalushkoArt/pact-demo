package com.example.priceservice.adapter.api;

import com.example.priceservice.adapter.api.model.OrderBookDto;
import com.example.priceservice.adapter.api.model.OrderDto;
import com.example.priceservice.domain.model.OrderBook;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.stream.Collectors;

/**
 * Mapper for converting between OrderBook domain model and OrderBookDto.
 * This adapter handles the translation between domain and API models.
 */
@Component
public class OrderBookMapper {

    /**
     * Converts a domain OrderBook to an OrderBookDto
     *
     * @param orderBook the domain model
     * @return the DTO
     */
    public OrderBookDto toDto(OrderBook orderBook) {
        if (orderBook == null) {
            return null;
        }
        
        return OrderBookDto.builder()
                .instrumentId(orderBook.getInstrumentId())
                .bidOrders(orderBook.getBidOrders().stream()
                        .map(this::toOrderDto)
                        .collect(Collectors.toList()))
                .askOrders(orderBook.getAskOrders().stream()
                        .map(this::toOrderDto)
                        .collect(Collectors.toList()))
                .lastUpdated(orderBook.getLastUpdated().atOffset(ZoneOffset.UTC))
                .build();
    }

    /**
     * Converts a domain Order to an OrderDto
     *
     * @param order the domain model
     * @return the DTO
     */
    private OrderDto toOrderDto(OrderBook.Order order) {
        return OrderDto.builder()
                .price(order.getPrice())
                .volume(order.getVolume())
                .build();
    }

    /**
     * Converts an OrderBookDto to a domain OrderBook
     *
     * @param orderBookDto the DTO
     * @return the domain model
     */
    public OrderBook toEntity(OrderBookDto orderBookDto) {
        if (orderBookDto == null) {
            return null;
        }
        
        return OrderBook.builder()
                .instrumentId(orderBookDto.getInstrumentId())
                .bidOrders(orderBookDto.getBidOrders().stream()
                        .map(this::toOrder)
                        .collect(Collectors.toList()))
                .askOrders(orderBookDto.getAskOrders().stream()
                        .map(this::toOrder)
                        .collect(Collectors.toList()))
                .lastUpdated(orderBookDto.getLastUpdated().toInstant())
                .build();
    }

    /**
     * Converts an OrderDto to a domain Order
     *
     * @param orderDto the DTO
     * @return the domain model
     */
    private OrderBook.Order toOrder(OrderDto orderDto) {
        return OrderBook.Order.builder()
                .price(orderDto.getPrice())
                .volume(orderDto.getVolume())
                .build();
    }
}
