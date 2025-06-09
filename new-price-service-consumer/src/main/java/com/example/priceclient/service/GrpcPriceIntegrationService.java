package com.example.priceclient.service;

import com.example.priceclient.grpc.client.GrpcPriceClient;
import com.example.priceservice.grpc.GetAllPricesResponse;
import com.example.priceservice.grpc.Price;
import com.example.priceservice.grpc.PriceUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service that integrates gRPC clients for price and order book operations.
 * This service demonstrates how to use the gRPC clients in a Spring Boot application.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GrpcPriceIntegrationService {
    private final GrpcPriceClient grpcPriceClient;

    /**
     * Retrieves all prices using gRPC
     *
     * @return list of prices
     */
    public List<Price> getAllPrices() {
        log.info("Fetching all prices via gRPC");

        Optional<GetAllPricesResponse> response = grpcPriceClient.getAllPrices(null, null);
        return response.map(GetAllPricesResponse::getPricesList)
                .orElse(List.of());
    }

    /**
     * Retrieves all prices with pagination using gRPC
     *
     * @param page page number
     * @param size page size
     * @return paginated response with prices
     */
    public Optional<GetAllPricesResponse> getAllPricesWithPagination(int page, int size) {
        log.info("Fetching prices via gRPC with pagination - page: {}, size: {}", page, size);

        return grpcPriceClient.getAllPrices(page, size);
    }

    /**
     * Retrieves a specific price by instrument ID using gRPC
     *
     * @param instrumentId the instrument identifier
     * @return Optional containing the price if found
     */
    public Optional<Price> getPrice(String instrumentId) {
        log.info("Fetching price for instrument {} via gRPC", instrumentId);

        return grpcPriceClient.getPrice(instrumentId);
    }

    /**
     * Subscribes to real-time price updates for specified instruments
     *
     * @param instrumentIds list of instrument IDs to subscribe to
     * @return list of price updates
     */
    public List<PriceUpdate> subscribeToPriceUpdates(List<String> instrumentIds) {
        log.info("Subscribing to price updates for instruments {} via gRPC", instrumentIds);

        return grpcPriceClient.streamPrices(instrumentIds);
    }
}
