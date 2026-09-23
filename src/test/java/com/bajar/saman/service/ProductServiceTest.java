package com.bajar.saman.service;

import com.bajar.saman.entity.*;
import com.bajar.saman.exception.*;
import com.bajar.saman.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock ProductRepository products;
    @Mock CategoryRepository categories;
    @Mock ProductAuthorizationPolicy policy;
    @Mock ShopRepository shops;
    @InjectMocks ProductService service;

    @Test void managerCreatesProductInsideSelectedShop() {
        User actor = user(); Shop shop = activeShop(); Category category = new Category("Grocery","grocery",null);
        UUID categoryId=UUID.randomUUID();
        when(shops.findById(shop.getId())).thenReturn(Optional.of(shop));
        when(categories.findById(categoryId)).thenReturn(Optional.of(category));
        when(products.findBySlug("rice")).thenReturn(Optional.empty());
        when(products.save(any())).thenAnswer(i->i.getArgument(0));
        Product result=service.createProduct(actor,shop.getId(),categoryId,"Rice","Local",BigDecimal.TEN,"RICE-1",5);
        assertThat(result.getShop()).isSameAs(shop);
        verify(policy).requireCanCreate(actor,shop);
    }

    @Test void unknownShopIsHiddenAsNotFound() {
        UUID shopId=UUID.randomUUID();
        when(shops.findById(shopId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createProduct(user(),shopId,UUID.randomUUID(),"Rice",null,BigDecimal.TEN,"R",1))
                .isInstanceOf(ShopNotFoundException.class);
        verifyNoInteractions(categories);
    }

    @Test void invalidPriceFailsBeforeShopLookup() {
        assertThatThrownBy(() -> service.createProduct(user(),UUID.randomUUID(),UUID.randomUUID(),"Rice",null,BigDecimal.ZERO,"R",1))
                .isInstanceOf(InvalidProductDataException.class);
        verifyNoInteractions(shops);
    }

    @Test void updatePriceUsesShopAuthorization() {
        Product product=product(activeShop()); UUID id=UUID.randomUUID(); User actor=user();
        when(products.findById(id)).thenReturn(Optional.of(product));
        assertThat(service.updatePrice(actor,id,new BigDecimal("12.50")).getPrice()).isEqualByComparingTo("12.50");
        verify(policy).requireCanManage(actor,product);
    }

    @Test void inactiveProductIsNotPublicBySlug() {
        when(products.findBySlugAndActiveTrue("hidden")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getProductBySlug("hidden")).isInstanceOf(ProductNotFoundException.class);
    }

    private User user(){User u=new User(UUID.randomUUID()+"@test","hash");ReflectionTestUtils.setField(u,"id",UUID.randomUUID());return u;}
    private Shop activeShop(){Shop s=new Shop("Gudri Shop","gudri-shop",UUID.randomUUID(),"9800000000","Gudri","Biratnagar","Morang",new BigDecimal("26.4525"),new BigDecimal("87.2718"));ReflectionTestUtils.setField(s,"id",UUID.randomUUID());s.approve();return s;}
    private Product product(Shop s){Product p=new Product(null,"Rice","rice",BigDecimal.TEN,"R",5);p.setShop(s);return p;}
}
