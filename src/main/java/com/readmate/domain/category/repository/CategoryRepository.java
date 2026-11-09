package com.readmate.domain.category.repository;

import com.readmate.domain.category.entity.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderByDisplayOrderAscIdAsc();

    /** 파생 쿼리(findByParent_Id)는 Hibernate 가 불필요한 self-join 을 붙여서, FK 컬럼을 직접 비교하도록 명시 */
    @Query("select c from Category c where c.parent.id = :parentId order by c.displayOrder, c.id")
    List<Category> findChildren(@Param("parentId") Long parentId);

    @Query("select count(c) > 0 from Category c where c.parent.id = :parentId")
    boolean hasChildren(@Param("parentId") Long parentId);

    /** 대분류(parent_id IS NULL)끼리의 이름 중복은 UNIQUE(parent_id, name)이 막지 못한다(NULL ≠ NULL) → 서비스에서 확인 */
    boolean existsByParentIsNullAndName(String name);

    boolean existsByParent_IdAndName(Long parentId, String name);
}
