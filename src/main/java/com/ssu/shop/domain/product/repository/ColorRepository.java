package com.ssu.shop.domain.product.repository;

import com.ssu.shop.domain.product.entity.Color;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ColorRepository extends JpaRepository<Color, Long> {

    List<Color> findAllByOrderByDisplayOrderAsc();
}
