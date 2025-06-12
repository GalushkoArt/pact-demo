package com.example.priceservice.domain.service;

import com.example.priceservice.adapter.kafka.PriceKafkaProducer;
import com.example.priceservice.adapter.kafka.ProtoPriceKafkaProducer;
import com.example.priceservice.domain.model.OrderBook;
import com.example.priceservice.domain.model.Price;
import com.example.priceservice.domain.port.PriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service implementation for price-related operations.
 * This service implements the business logic for the price domain.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PriceServiceImpl {

    private final PriceRepository priceRepository;
    private final PriceKafkaProducer priceKafkaProducer;
    private final ProtoPriceKafkaProducer protoPriceKafkaProducer;

    /**
     * Retrieves the price for a specific instrument
     *
     * @param instrumentId the unique identifier of the instrument
     * @return the price if available
     */
    public Optional<Price> getPrice(String instrumentId) {
        log.debug("Getting price for instrument: {}", instrumentId);
        return priceRepository.findByInstrumentId(instrumentId);
    }

    /**
     * Retrieves all available prices
     *
     * @return list of all prices
     */
    public List<Price> getAllPrices() {
        log.debug("Getting all prices");
        return priceRepository.findAll();
    }

    /**
     * Creates or updates the price for a specific instrument
     *
     * @param price the price to save
     * @return the saved price
     */
    public Price savePrice(Price price) {
        log.debug("Saving price for instrument: {}", price.getInstrumentId());
        price.setLastUpdated(Instant.now());
        Price saved = priceRepository.save(price);
        // Publish update event to Kafka
        priceKafkaProducer.sendPriceUpdate(saved);
        protoPriceKafkaProducer.sendPriceUpdate(saved);
        return saved;
    }

    /**
     * Deletes the price for a specific instrument
     *
     * @param instrumentId the unique identifier of the instrument
     * @return true if deleted successfully
     */
    public boolean deletePrice(String instrumentId) {
        log.debug("Deleting price for instrument: {}", instrumentId);
        return priceRepository.deleteByInstrumentId(instrumentId);
    }

    /**
     * Retrieves the order book for a specific instrument
     *
     * @param instrumentId the unique identifier of the instrument
     * @return the order book if available
     */
    public Optional<OrderBook> getOrderBook(String instrumentId) {
        log.debug("Getting order book for instrument: {}", instrumentId);
        return priceRepository.findOrderBookByInstrumentId(instrumentId);
    }
    
    /**
     * Creates or updates the order book for a specific instrument
     *
     * @param orderBook the order book to save
     * @return the saved order book
     */
    public OrderBook saveOrderBook(OrderBook orderBook) {
        log.debug("Saving order book for instrument: {}", orderBook.getInstrumentId());
        orderBook.setLastUpdated(Instant.now());
        return priceRepository.saveOrderBook(orderBook);
    }
}
