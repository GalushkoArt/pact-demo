package com.example.priceservice.adapter.persistence;

import com.example.priceservice.adapter.persistence.entity.OrderBookEntity;
import com.example.priceservice.adapter.persistence.entity.OrderEntity;
import com.example.priceservice.adapter.persistence.repository.OrderBookJpaRepository;
import com.example.priceservice.domain.model.OrderBook;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Service for managing order books in the database.
 * This component handles the creation and updating of order books and their orders.
 */
@Component
public class OrderBookService {

    private final OrderBookJpaRepository orderBookJpaRepository;

    public OrderBookService(OrderBookJpaRepository orderBookJpaRepository) {
        this.orderBookJpaRepository = orderBookJpaRepository;
    }

    /**
     * Saves an order book to the database
     *
     * @param orderBook the domain model to save
     * @return the saved entity
     */
    @Transactional
    public OrderBookEntity saveOrderBook(OrderBook orderBook) {
        OrderBookEntity entity = mapToOrderBookEntity(orderBook);
        entity.setLastUpdated(Instant.now());
        return orderBookJpaRepository.save(entity);
    }

    /**
     * Finds an order book by instrument ID
     *
     * @param instrumentId the instrument ID to search for
     * @return the order book entity if found
     */
    public Optional<OrderBookEntity> findByInstrumentId(String instrumentId) {
        return orderBookJpaRepository.findById(instrumentId);
    }

    /**
     * Maps a domain model to a JPA entity
     *
     * @param orderBook the domain model
     * @return the JPA entity
     */
    private OrderBookEntity mapToOrderBookEntity(OrderBook orderBook) {
        OrderBookEntity entity = OrderBookEntity.builder()
                .instrumentId(orderBook.getInstrumentId())
                .lastUpdated(orderBook.getLastUpdated())
                .build();

        // Clear existing orders to avoid duplicates
        entity.getBidOrders().clear();
        entity.getAskOrders().clear();

        // Add bid orders
        orderBook.getBidOrders().forEach(order -> {
            OrderEntity orderEntity = OrderEntity.builder()
                    .orderBook(entity)
                    .price(order.getPrice())
                    .volume(order.getVolume())
                    .orderType(OrderEntity.OrderType.BID)
                    .build();
            entity.getBidOrders().add(orderEntity);
        });

        // Add ask orders
        orderBook.getAskOrders().forEach(order -> {
            OrderEntity orderEntity = OrderEntity.builder()
                    .orderBook(entity)
                    .price(order.getPrice())
                    .volume(order.getVolume())
                    .orderType(OrderEntity.OrderType.ASK)
                    .build();
            entity.getAskOrders().add(orderEntity);
        });

        return entity;
    }
}
