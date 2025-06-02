package com.example.priceservice.adapter.persistence.repository;

import com.example.priceservice.adapter.persistence.entity.OrderBookEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA repository for order book entities.
 */
@Repository
public interface OrderBookJpaRepository extends JpaRepository<OrderBookEntity, String> {
    // Spring Data JPA will automatically implement basic CRUD operations
}
