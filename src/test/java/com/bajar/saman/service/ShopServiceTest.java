package com.bajar.saman.service;

import com.bajar.saman.entity.*;
import com.bajar.saman.exception.InvalidProductDataException;
import com.bajar.saman.exception.IdempotencyConflictException;
import com.bajar.saman.exception.ShopNotFoundException;
import com.bajar.saman.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {
    @Mock ShopRepository shops;
    @Mock ShopMemberRepository members;

    @Test void applicationCreatesPendingShopAndOwnerMembership() {
        User owner=user();
        when(shops.findBySlug("ram-kirana")).thenReturn(Optional.empty());
        when(shops.save(any())).thenAnswer(inv->{Shop s=inv.getArgument(0);ReflectionTestUtils.setField(s,"id",UUID.randomUUID());return s;});
        UUID key=UUID.randomUUID();
        when(shops.findByApplicationKey(key)).thenReturn(Optional.empty());
        Shop result=service().apply(owner,key,"Ram Kirana","9800000000","Gudri","Biratnagar","Morang",new BigDecimal("26.4525"),new BigDecimal("87.2718"));
        assertThat(result.getStatus()).isEqualTo(ShopStatus.PENDING_APPROVAL);
        verify(members).save(argThat(m->m.getUser()==owner && m.getRole()==ShopMemberRole.OWNER));
    }

    @Test void onlyPendingShopCanBeApproved() {
        Shop shop=shop(); shop.approve(); when(shops.findById(shop.getId())).thenReturn(Optional.of(shop));
        assertThatThrownBy(() -> service().approve(shop.getId())).isInstanceOf(InvalidProductDataException.class);
    }

    @Test void retryWithSameKeyAndSamePayloadReturnsOriginalShop() {
        User owner=user(); Shop shop=shop(); UUID key=shop.getApplicationKey();
        when(shops.findByApplicationKey(key)).thenReturn(Optional.of(shop));
        when(members.findByShopIdAndUserIdAndActiveTrue(shop.getId(), owner.getId()))
                .thenReturn(Optional.of(new ShopMember(shop, owner, ShopMemberRole.OWNER)));

        Shop result=service().apply(owner,key,"Ram Kirana","9800000000","Gudri",
                "Biratnagar","Morang",new BigDecimal("26.452500"),new BigDecimal("87.271800"));

        assertThat(result).isSameAs(shop);
        verify(shops,never()).save(any());
        verify(members,never()).save(any());
    }

    @Test void retryWithSameKeyAndDifferentPayloadIsRejected() {
        User owner=user(); Shop shop=shop(); UUID key=shop.getApplicationKey();
        when(shops.findByApplicationKey(key)).thenReturn(Optional.of(shop));
        when(members.findByShopIdAndUserIdAndActiveTrue(shop.getId(), owner.getId()))
                .thenReturn(Optional.of(new ShopMember(shop, owner, ShopMemberRole.OWNER)));

        assertThatThrownBy(() -> service().apply(owner,key,"Different Shop","9800000000","Gudri",
                "Biratnagar","Morang",new BigDecimal("26.4525"),new BigDecimal("87.2718")))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test void applicationKeyOwnedByAnotherUserIsNotExposed() {
        User stranger=user(); Shop shop=shop(); UUID key=shop.getApplicationKey();
        when(shops.findByApplicationKey(key)).thenReturn(Optional.of(shop));
        when(members.findByShopIdAndUserIdAndActiveTrue(shop.getId(), stranger.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().apply(stranger,key,"Ram Kirana","9800000000","Gudri",
                "Biratnagar","Morang",new BigDecimal("26.4525"),new BigDecimal("87.2718")))
                .isInstanceOf(ShopNotFoundException.class);
    }

    private ShopService service(){return new ShopService(shops,members);}
    private User user(){User u=new User("owner@test","hash");ReflectionTestUtils.setField(u,"id",UUID.randomUUID());return u;}
    private Shop shop(){Shop s=new Shop("Ram Kirana","ram-kirana",UUID.randomUUID(),"9800000000","Gudri","Biratnagar","Morang",new BigDecimal("26.4525"),new BigDecimal("87.2718"));ReflectionTestUtils.setField(s,"id",UUID.randomUUID());return s;}
}
