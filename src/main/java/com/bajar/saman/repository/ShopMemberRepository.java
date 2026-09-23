package com.bajar.saman.repository;

import com.bajar.saman.entity.ShopMember;
import com.bajar.saman.entity.ShopMemberId;
import com.bajar.saman.entity.ShopMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopMemberRepository extends JpaRepository<ShopMember, ShopMemberId> {
    List<ShopMember> findByUserIdAndActiveTrue(UUID userId);
    Optional<ShopMember> findByShopIdAndUserIdAndActiveTrue(UUID shopId, UUID userId);
    boolean existsByShopIdAndUserIdAndRoleInAndActiveTrue(
            UUID shopId, UUID userId, Collection<ShopMemberRole> roles);
}
