package com.bajar.saman.repository;

import com.bajar.saman.entity.RefreshSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSession s join fetch s.user where s.tokenHash = :tokenHash")
    Optional<RefreshSession> findForUpdateByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("update RefreshSession s set s.revokedAt = :now " +
            "where s.familyId = :familyId and s.revokedAt is null")
    int revokeActiveFamily(@Param("familyId") UUID familyId, @Param("now") LocalDateTime now);
}
