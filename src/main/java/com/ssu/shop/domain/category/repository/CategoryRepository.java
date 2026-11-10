package com.ssu.shop.domain.category.repository;

import com.ssu.shop.domain.category.entity.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderByDisplayOrderAscIdAsc();

    /** 파생 쿼리(findByParent_Id)는 Hibernate 가 불필요한 self-join 을 붙여서, FK 컬럼을 직접 비교하도록 명시 */
    @Query("select c from Category c where c.parent.id = :parentId order by c.displayOrder, c.id")
    List<Category> findChildren(@Param("parentId") Long parentId);
}
