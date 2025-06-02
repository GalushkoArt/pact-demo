package com.example.priceservice.domain.port;

import com.example.priceservice.domain.model.OrderBook;
import com.example.priceservice.domain.model.Price;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for price-related operations.
 * This port defines how the domain interacts with the persistence layer.
 */
public interface PriceRepository {

    /**
     * Finds a price by instrument ID
     *
     * @param instrumentId the unique identifier of the instrument
     * @return the price if found
     */
    Optional<Price> findByInstrumentId(String instrumentId);

    /**
     * Saves a price
     *
     * @param price the price to save
     * @return the saved price
     */
    Price save(Price price);

    /**
     * Deletes a price by instrument ID
     *
     * @param instrumentId the unique identifier of the instrument
     * @return true if deleted successfully
     */
    boolean deleteByInstrumentId(String instrumentId);

    /**
     * Finds all prices
     *
     * @return list of all prices
     */
    List<Price> findAll();

    /**
     * Finds an order book by instrument ID
     *
     * @param instrumentId the unique identifier of the instrument
     * @return the order book if found
     */
    Optional<OrderBook> findOrderBookByInstrumentId(String instrumentId);
    
    /**
     * Saves an order book
     *
     * @param orderBook the order book to save
     * @return the saved order book
     */
    OrderBook saveOrderBook(OrderBook orderBook);
}
