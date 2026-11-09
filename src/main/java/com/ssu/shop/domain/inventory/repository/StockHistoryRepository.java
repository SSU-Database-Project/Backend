package com.ssu.shop.domain.inventory.repository;

import com.ssu.shop.domain.inventory.entity.StockHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockHistoryRepository extends JpaRepository<StockHistory, Long> {
}
