package com.bajar.saman.controller;

import com.bajar.saman.config.SecurityConfig;
import com.bajar.saman.security.JwtAuthenticationFilter;
import com.bajar.saman.security.RateLimitFilter;
import com.bajar.saman.security.RestAccessDeniedHandler;
import com.bajar.saman.security.RestAuthenticationEntryPoint;
import com.bajar.saman.service.ShopService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ShopController.class, ShopAdminController.class})
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class ShopControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean ShopService shopService;
    @MockitoBean JpaMetamodelMappingContext jpaMappingContext;
    @MockitoBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean RateLimitFilter rateLimitFilter;

    @BeforeEach void passThroughMockedApplicationFilters() throws Exception {
        doAnswer(invocation -> {
            ((jakarta.servlet.FilterChain) invocation.getArgument(2))
                    .doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(rateLimitFilter).doFilter(any(), any(), any());
        doAnswer(invocation -> {
            ((jakarta.servlet.FilterChain) invocation.getArgument(2))
                    .doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test void anonymousCannotListOwnShops() throws Exception {
        mvc.perform(get("/api/shops/mine")).andExpect(status().isUnauthorized());
        verifyNoInteractions(shopService);
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotListPendingShops() throws Exception {
        mvc.perform(get("/api/admin/shops/pending")).andExpect(status().isForbidden());
        verifyNoInteractions(shopService);
    }
}
