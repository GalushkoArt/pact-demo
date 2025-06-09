package com.example.priceclient.grpc.client;

import com.example.priceservice.grpc.*;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * gRPC client for Price Service.
 * Provides methods to interact with the gRPC price service.
 */
@Component
@Slf4j
public class GrpcPriceClient {

    @GrpcClient("price-service")
    private PriceServiceGrpc.PriceServiceBlockingStub priceServiceStub;

    /**
     * Retrieves all prices from the gRPC service
     *
     * @param page page number (optional)
     * @param size page size (optional)
     * @return GetAllPricesResponse containing the prices
     */
    public Optional<GetAllPricesResponse> getAllPrices(Integer page, Integer size) {
        try {
            log.debug("Calling gRPC getAllPrices with page: {}, size: {}", page, size);

            GetAllPricesRequest.Builder requestBuilder = GetAllPricesRequest.newBuilder();
            if (page != null && page > 0) {
                requestBuilder.setPage(page);
            }
            if (size != null && size > 0) {
                requestBuilder.setSize(size);
            }

            GetAllPricesResponse response = priceServiceStub.getAllPrices(requestBuilder.build());
            log.debug("Received {} prices from gRPC service", response.getPricesCount());

            return Optional.of(response);

        } catch (StatusRuntimeException e) {
            log.error("gRPC error in getAllPrices: {}", e.getStatus().getDescription(), e);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Unexpected error in getAllPrices", e);
            return Optional.empty();
        }
    }

    /**
     * Retrieves a specific price by instrument ID
     *
     * @param instrumentId the instrument identifier
     * @return Optional containing the price if found
     */
    public Optional<Price> getPrice(String instrumentId) {
        try {
            log.debug("Calling gRPC getPrice for instrument: {}", instrumentId);

            GetPriceRequest request = GetPriceRequest.newBuilder()
                    .setInstrumentId(instrumentId)
                    .build();

            GetPriceResponse response = priceServiceStub.getPrice(request);
            log.debug("Received price for instrument: {}", instrumentId);

            return Optional.of(response.getPrice());

        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                log.debug("Price not found for instrument: {}", instrumentId);
                return Optional.empty();
            }
            log.error("gRPC error in getPrice for instrument {}: {}", instrumentId, e.getStatus().getDescription(), e);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Unexpected error in getPrice for instrument: {}", instrumentId, e);
            return Optional.empty();
        }
    }

    /**
     * Subscribes to price updates for specified instruments
     *
     * @param instrumentIds list of instrument IDs to subscribe to
     * @return list of price updates
     */
    public List<PriceUpdate> streamPrices(List<String> instrumentIds) {
        try {
            log.debug("Calling gRPC streamPrices for instruments: {}", instrumentIds);

            StreamPricesRequest request = StreamPricesRequest.newBuilder()
                    .addAllInstrumentIds(instrumentIds)
                    .build();

            // For this demo, we'll collect all updates and return them
            // In a real implementation, this would handle streaming responses
            List<PriceUpdate> updates = new java.util.ArrayList<>();
            priceServiceStub.streamPrices(request).forEachRemaining(updates::add);

            log.debug("Received {} price updates", updates.size());
            return updates;

        } catch (StatusRuntimeException e) {
            log.error("gRPC error in streamPrices: {}", e.getStatus().getDescription(), e);
            return List.of();
        } catch (Exception e) {
            log.error("Unexpected error in streamPrices", e);
            return List.of();
        }
    }
}

