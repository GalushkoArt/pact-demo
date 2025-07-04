package com.example.priceclient.service;

import com.example.priceservice.client.api.PricesApi;
import com.example.priceservice.client.api.model.PriceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for managing price subscriptions and retrieving price information.
 * This service uses the PriceServiceClient to communicate with the provider.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PriceService {
    private final PricesApi pricesApi;

    /**
     * Gets all available prices
     *
     * @return list of all prices
     */
    public List<PriceDto> getAllPrices() {
        log.debug("Getting all prices");
        return pricesApi.getAllPrices();
    }

    /**
     * Gets the price for a specific instrument
     *
     * @param instrumentId the unique identifier of the instrument
     * @return the price if available
     */
    public PriceDto getPriceForInstrument(String instrumentId) {
        log.debug("Getting price for instrument: {}", instrumentId);
        return pricesApi.getPrice(instrumentId);
    }

    /**
     * Updates the price for a specific instrument
     *
     * @param priceDto the price information to update
     * @return the updated price if successful
     */
    public PriceDto updatePrice(PriceDto priceDto) {
        log.debug("Updating price for instrument: {}", priceDto.getInstrumentId());
        return pricesApi.savePrice(priceDto.getInstrumentId(), priceDto);
    }
}
