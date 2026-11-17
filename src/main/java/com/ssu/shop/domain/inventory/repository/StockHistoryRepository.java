package com.ssu.shop.domain.inventory.repository;

import com.ssu.shop.domain.inventory.entity.StockHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockHistoryRepository extends JpaRepository<StockHistory, Long> {

    /** 옵션의 재고 이력을 최신순으로. id 순서가 곧 재고가 바뀐 순서다 (행을 잠근 채로 이력을 남기기 때문) */
    Page<StockHistory> findByOption_IdOrderByIdDesc(Long optionId, Pageable pageable);
}
