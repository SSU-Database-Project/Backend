package com.ssu.shop.domain.order.repository;

import com.ssu.shop.domain.order.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
}
