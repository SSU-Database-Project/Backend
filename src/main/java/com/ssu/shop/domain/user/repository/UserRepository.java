package com.ssu.shop.domain.user.repository;

import com.ssu.shop.domain.user.entity.Role;
import com.ssu.shop.domain.user.entity.User;
import com.ssu.shop.domain.user.entity.UserStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    /** uk_users_email 인덱스 사용 */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * 정지·탈퇴·정보 수정 전에 회원 행을 잠근다 (FOR UPDATE).
     * 트랜잭션에서 회원을 처음 읽는 곳이어야 한다 → 영속성 컨텍스트에 예전 값이 없어 잠근 뒤의 최신 값을 받는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    /**
     * 주문할 때 구매자와 판매자들의 상태를 잠그고(FOR SHARE) 최신 값으로 읽는다. id 오름차순.
     * 엔티티가 아니라 값만 받는다: 장바구니 조회로 이미 올라온 엔티티는 잠가 읽어도 예전 값이 그대로 남기 때문이다.
     */
    @Query(value = "SELECT id, status FROM users WHERE id IN (:ids) ORDER BY id FOR SHARE", nativeQuery = true)
    List<UserStatusRow> lockStatuses(@Param("ids") Collection<Long> ids);

    interface UserStatusRow {
        Long getId();

        String getStatus();
    }

    @Query("""
            select u from User u
            where (:role is null or u.role = :role)
              and (:status is null or u.status = :status)
              and (:keyword is null or u.email like concat('%', :keyword, '%')
                                    or u.name  like concat('%', :keyword, '%'))
            """)
    Page<User> search(@Param("role") Role role, @Param("status") UserStatus status,
                      @Param("keyword") String keyword, Pageable pageable);
}
