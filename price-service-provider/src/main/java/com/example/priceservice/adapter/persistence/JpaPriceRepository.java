package com.example.priceservice.adapter.persistence;

import com.example.priceservice.adapter.persistence.entity.OrderBookEntity;
import com.example.priceservice.adapter.persistence.entity.OrderEntity;
import com.example.priceservice.adapter.persistence.entity.PriceEntity;
import com.example.priceservice.adapter.persistence.repository.OrderBookJpaRepository;
import com.example.priceservice.adapter.persistence.repository.PriceJpaRepository;
import com.example.priceservice.domain.model.OrderBook;
import com.example.priceservice.domain.model.Price;
import com.example.priceservice.domain.port.PriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JPA implementation of the PriceRepository port.
 * This adapter provides database storage for prices and order books.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class JpaPriceRepository implements PriceRepository {

    private final PriceJpaRepository priceJpaRepository;
    private final OrderBookJpaRepository orderBookJpaRepository;

    @Override
    public Optional<Price> findByInstrumentId(String instrumentId) {
        log.debug("Finding price by instrument ID: {}", instrumentId);
        return priceJpaRepository.findById(instrumentId)
                .map(this::mapToPrice);
    }

    @Override
    @Transactional
    public Price save(Price price) {
        log.debug("Saving price for instrument: {}", price.getInstrumentId());
        PriceEntity entity = mapToPriceEntity(price);
        entity.setLastUpdated(Instant.now());
        PriceEntity savedEntity = priceJpaRepository.save(entity);
        return mapToPrice(savedEntity);
    }

    @Override
    @Transactional
    public boolean deleteByInstrumentId(String instrumentId) {
        log.debug("Deleting price for instrument: {}", instrumentId);
        if (priceJpaRepository.existsById(instrumentId)) {
            priceJpaRepository.deleteById(instrumentId);
            return true;
        }
        return false;
    }

    @Override
    public List<Price> findAll() {
        log.debug("Finding all prices");
        return priceJpaRepository.findAll().stream()
                .map(this::mapToPrice)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<OrderBook> findOrderBookByInstrumentId(String instrumentId) {
        log.debug("Finding order book by instrument ID: {}", instrumentId);
        return orderBookJpaRepository.findById(instrumentId)
                .map(this::mapToOrderBook);
    }
    
    @Override
    @Transactional
    public OrderBook saveOrderBook(OrderBook orderBook) {
        log.debug("Saving order book for instrument: {}", orderBook.getInstrumentId());
        OrderBookEntity entity = mapToOrderBookEntity(orderBook);
        entity.setLastUpdated(Instant.now());
        OrderBookEntity savedEntity = orderBookJpaRepository.save(entity);
        return mapToOrderBook(savedEntity);
    }

    private Price mapToPrice(PriceEntity entity) {
        return Price.builder()
                .instrumentId(entity.getInstrumentId())
                .bidPrice(entity.getBidPrice())
                .askPrice(entity.getAskPrice())
                .lastUpdated(entity.getLastUpdated())
                .build();
    }

    private PriceEntity mapToPriceEntity(Price price) {
        return PriceEntity.builder()
                .instrumentId(price.getInstrumentId())
                .bidPrice(price.getBidPrice())
                .askPrice(price.getAskPrice())
                .lastUpdated(price.getLastUpdated())
                .build();
    }

    private OrderBook mapToOrderBook(OrderBookEntity entity) {
        List<OrderBook.Order> bidOrders = entity.getBidOrders().stream()
                .map(orderEntity -> new OrderBook.Order(orderEntity.getPrice(), orderEntity.getVolume()))
                .collect(Collectors.toList());

        List<OrderBook.Order> askOrders = entity.getAskOrders().stream()
                .map(orderEntity -> new OrderBook.Order(orderEntity.getPrice(), orderEntity.getVolume()))
                .collect(Collectors.toList());

        return OrderBook.builder()
                .instrumentId(entity.getInstrumentId())
                .bidOrders(bidOrders)
                .askOrders(askOrders)
                .lastUpdated(entity.getLastUpdated())
                .build();
    }

    private OrderBookEntity mapToOrderBookEntity(OrderBook orderBook) {
        OrderBookEntity entity = OrderBookEntity.builder()
                .instrumentId(orderBook.getInstrumentId())
                .lastUpdated(orderBook.getLastUpdated())
                .build();

        List<OrderEntity> bidOrders = orderBook.getBidOrders().stream()
                .map(order -> OrderEntity.builder()
                        .orderBook(entity)
                        .price(order.getPrice())
                        .volume(order.getVolume())
                        .orderType(OrderEntity.OrderType.BID)
                        .build())
                .collect(Collectors.toList());

        List<OrderEntity> askOrders = orderBook.getAskOrders().stream()
                .map(order -> OrderEntity.builder()
                        .orderBook(entity)
                        .price(order.getPrice())
                        .volume(order.getVolume())
                        .orderType(OrderEntity.OrderType.ASK)
                        .build())
                .collect(Collectors.toList());

        entity.setBidOrders(bidOrders);
        entity.setAskOrders(askOrders);

        return entity;
    }
}
