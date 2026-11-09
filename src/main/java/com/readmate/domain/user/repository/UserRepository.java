package com.readmate.domain.user.repository;

import com.readmate.domain.user.entity.Role;
import com.readmate.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    /** uk_users_email 인덱스 사용 */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("""
            select u from User u
            where (:role is null or u.role = :role)
              and (:keyword is null or u.email like concat('%', :keyword, '%')
                                    or u.name  like concat('%', :keyword, '%'))
            """)
    Page<User> search(@Param("role") Role role, @Param("keyword") String keyword, Pageable pageable);
}
