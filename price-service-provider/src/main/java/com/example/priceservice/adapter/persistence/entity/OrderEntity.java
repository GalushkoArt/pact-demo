package com.example.priceservice.adapter.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * JPA entity for storing order information in the database.
 */
@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_book_id", nullable = false)
    private OrderBookEntity orderBook;

    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "volume", nullable = false, precision = 19, scale = 4)
    private BigDecimal volume;

    @Column(name = "order_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderType orderType;

    /**
     * Enum representing the type of order (BID or ASK)
     */
    public enum OrderType {
        BID, ASK
    }
}
