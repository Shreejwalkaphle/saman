package com.bajar.saman.repository;

import com.bajar.saman.entity.UserRole;
import com.bajar.saman.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByUser_Id(UUID userId);
    /**
     * Returns role NAMES directly via a JOIN, instead of full UserRole/Role
     * entities. This exists specifically to avoid the LazyInitializationException
     * that findByUser_Id() + lazy Role access triggers when called from
     * JwtAuthenticationFilter (see that class's comment for the full story) — by
     * doing the join in SQL and projecting straight to String, there's no lazy
     * field left to touch after the query completes, so no open session is needed
     * afterward at all. Also a minor efficiency win: only pulls the 4-ish bytes of
     * a role name per row, not entire Role/User entity graphs.
     */
    @Query(
            "SELECT r.name FROM UserRole ur JOIN ur.role r WHERE ur.user.id = :userId")
    List<String> findRoleNamesByUserId(@org.springframework.data.repository.query.Param("userId") UUID userId);
}