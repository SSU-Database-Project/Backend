package com.ssu.shop.domain.product.repository;

import com.ssu.shop.domain.product.entity.Size;
import com.ssu.shop.domain.product.entity.SizeGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SizeRepository extends JpaRepository<Size, Long> {

    List<Size> findAllBySizeGroupOrderByDisplayOrderAsc(SizeGroup sizeGroup);

    List<Size> findAllByOrderBySizeGroupAscDisplayOrderAsc();
}
