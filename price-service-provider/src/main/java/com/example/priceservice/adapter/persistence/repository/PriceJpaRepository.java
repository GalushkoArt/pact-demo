package com.example.priceservice.adapter.persistence.repository;

import com.example.priceservice.adapter.persistence.entity.PriceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA repository for price entities.
 */
@Repository
public interface PriceJpaRepository extends JpaRepository<PriceEntity, String> {
    // Spring Data JPA will automatically implement basic CRUD operations
    // and methods like findById, save, deleteById, etc.
}
