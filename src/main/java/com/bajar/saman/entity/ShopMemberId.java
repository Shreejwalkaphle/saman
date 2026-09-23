package com.bajar.saman.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ShopMemberId implements Serializable {
    private UUID shopId;
    private UUID userId;
    protected ShopMemberId() {}
    public ShopMemberId(UUID shopId, UUID userId) { this.shopId = shopId; this.userId = userId; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShopMemberId that)) return false;
        return Objects.equals(shopId, that.shopId) && Objects.equals(userId, that.userId);
    }
    @Override public int hashCode() { return Objects.hash(shopId, userId); }
}
