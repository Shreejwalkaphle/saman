package com.bajar.saman.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "shop_members")
public class ShopMember {
    @EmbeddedId private ShopMemberId id;
    @ManyToOne(fetch = FetchType.LAZY) @MapsId("shopId")
    @JoinColumn(name = "shop_id", nullable = false) private Shop shop;
    @ManyToOne(fetch = FetchType.LAZY) @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false) private User user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ShopMemberRole role;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(name = "joined_at", nullable = false) private LocalDateTime joinedAt;

    protected ShopMember() {}
    public ShopMember(Shop shop, User user, ShopMemberRole role) {
        this.shop = shop; this.user = user; this.role = role;
        this.id = new ShopMemberId(shop.getId(), user.getId());
        this.joinedAt = LocalDateTime.now();
    }
    public Shop getShop() { return shop; }
    public User getUser() { return user; }
    public ShopMemberRole getRole() { return role; }
    public boolean isActive() { return active; }
}
