package com.ssu.shop.domain.inventory.repository;

import com.ssu.shop.domain.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

/** [BE-2] 재고 차감용 락/조건부 UPDATE 메서드는 BE-2 가 추가한다. */
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
}
