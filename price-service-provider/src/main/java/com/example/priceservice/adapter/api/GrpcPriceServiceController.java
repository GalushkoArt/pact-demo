package com.example.priceservice.adapter.api;

import com.example.priceservice.domain.service.PriceServiceImpl;
import com.example.priceservice.grpc.*;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * gRPC service implementation for Price Service.
 * Provides price-related operations via gRPC protocol.
 */
@GrpcService
@RequiredArgsConstructor
@Slf4j
public class GrpcPriceServiceController extends PriceServiceGrpc.PriceServiceImplBase {

    private final PriceServiceImpl priceService;

    @Override
    public void getAllPrices(GetAllPricesRequest request, StreamObserver<GetAllPricesResponse> responseObserver) {
        try {
            log.debug("gRPC getAllPrices called with page: {}, size: {}", request.getPage(), request.getSize());

            List<com.example.priceservice.domain.model.Price> prices = priceService.getAllPrices();

            // Apply pagination if requested
            int page = request.getPage() > 0 ? request.getPage() : 1;
            int size = request.getSize() > 0 ? request.getSize() : prices.size();
            int startIndex = (page - 1) * size;
            int endIndex = Math.min(startIndex + size, prices.size());

            List<com.example.priceservice.domain.model.Price> paginatedPrices =
                    startIndex < prices.size() ? prices.subList(startIndex, endIndex) : List.of();

            List<Price> grpcPrices = paginatedPrices.stream()
                    .map(this::convertToGrpcPrice)
                    .collect(Collectors.toList());

            GetAllPricesResponse response = GetAllPricesResponse.newBuilder()
                    .addAllPrices(grpcPrices)
                    .setTotalCount(prices.size())
                    .setPage(page)
                    .setSize(size)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in getAllPrices", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to retrieve prices: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getPrice(GetPriceRequest request, StreamObserver<GetPriceResponse> responseObserver) {
        try {
            log.debug("gRPC getPrice called for instrument: {}", request.getInstrumentId());

            Optional<com.example.priceservice.domain.model.Price> priceOpt =
                    priceService.getPrice(request.getInstrumentId());

            if (priceOpt.isPresent()) {
                Price grpcPrice = convertToGrpcPrice(priceOpt.get());
                GetPriceResponse response = GetPriceResponse.newBuilder()
                        .setPrice(grpcPrice)
                        .build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();
            } else {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Price not found for instrument: " + request.getInstrumentId())
                        .asRuntimeException());
            }

        } catch (Exception e) {
            log.error("Error in getPrice for instrument: {}", request.getInstrumentId(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to retrieve price: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void streamPrices(StreamPricesRequest request, StreamObserver<PriceUpdate> responseObserver) {
        try {
            log.debug("gRPC streamPrices called for instruments: {}", request.getInstrumentIdsList());

            // For demonstration purposes, we'll send current prices and complete
            // In a real implementation, this would maintain an active stream
            for (String instrumentId : request.getInstrumentIdsList()) {
                Optional<com.example.priceservice.domain.model.Price> priceOpt =
                        priceService.getPrice(instrumentId);

                if (priceOpt.isPresent()) {
                    Price grpcPrice = convertToGrpcPrice(priceOpt.get());
                    PriceUpdate update = PriceUpdate.newBuilder()
                            .setPrice(grpcPrice)
                            .setUpdateType(UpdateType.UPDATED)
                            .build();

                    responseObserver.onNext(update);
                }
            }

            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in streamPrices", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to stream prices: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * Converts domain Price model to gRPC Price message
     */
    private Price convertToGrpcPrice(com.example.priceservice.domain.model.Price domainPrice) {
        Timestamp timestamp = Timestamp.newBuilder()
                .setSeconds(domainPrice.getLastUpdated().getEpochSecond())
                .setNanos(domainPrice.getLastUpdated().getNano())
                .build();

        return Price.newBuilder()
                .setInstrumentId(domainPrice.getInstrumentId())
                .setBidPrice(domainPrice.getBidPrice().doubleValue())
                .setAskPrice(domainPrice.getAskPrice().doubleValue())
                .setLastUpdated(timestamp)
                .build();
    }
}

