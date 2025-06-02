package com.example.priceservice.adapter.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity for storing order book information in the database.
 */
@Entity
@Table(name = "order_books")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderBookEntity {

    @Id
    @Column(name = "instrument_id")
    private String instrumentId;

    @OneToMany(mappedBy = "orderBook", cascade = CascadeType.ALL, orphanRemoval = true)
    @SQLRestriction("order_type = 'BID'")
    @Builder.Default
    private List<OrderEntity> bidOrders = new ArrayList<>();

    @OneToMany(mappedBy = "orderBook", cascade = CascadeType.ALL, orphanRemoval = true)
    @SQLRestriction("order_type = 'ASK'")
    @Builder.Default
    private List<OrderEntity> askOrders = new ArrayList<>();

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;
}
