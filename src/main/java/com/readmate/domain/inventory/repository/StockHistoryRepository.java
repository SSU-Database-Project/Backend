package com.readmate.domain.inventory.repository;

import com.readmate.domain.inventory.entity.StockHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockHistoryRepository extends JpaRepository<StockHistory, Long> {
}
