package com.sentio.user_service.identity.user.internal.repository;

import com.sentio.user_service.identity.user.api.enums.PlatformRole;
import com.sentio.user_service.identity.user.internal.entity.User;
import lombok.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @NonNull Page<User> findAll(@NonNull Pageable pageable);

    // Плюс до @SQLRestriction на User - рахує лише АКТИВНИХ адмінів, тому
    // soft-deleted admin не рахується як "запасний" при демоуті останнього.
    long countByPlatformRole(PlatformRole platformRole);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.platformRole = :platformRole")
    List<User> findActiveByPlatformRoleWithLock(@Param("platformRole") PlatformRole platformRole);

    @Query(value = """
        SELECT * FROM users u
        WHERE (:email IS NULL OR u.email ILIKE CONCAT('%', :email, '%'))
        AND (:includeDeleted = true OR u.deleted_at IS NULL)
        """, countQuery = """
        SELECT COUNT(*) FROM users u
        WHERE (:email IS NULL OR u.email ILIKE CONCAT('%', :email, '%'))
        AND (:includeDeleted = true OR u.deleted_at IS NULL)
        """, nativeQuery = true)
    Page<User> search(@Param("email") String email, @Param("includeDeleted") Boolean includeDeleted, Pageable pageable);

    @Query(value = "SELECT * FROM users u WHERE u.id = :id", nativeQuery = true)
    Optional<User> findByIdIncludingDeleted(@Param("id") long id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);
}
