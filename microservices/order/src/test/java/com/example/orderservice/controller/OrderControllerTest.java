package com.example.orderservice.controller;

import com.example.orderservice.auth.AppUserRepository;
import com.example.orderservice.config.SecurityConfig;
import com.example.orderservice.model.Order;
import com.example.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.util.List;
import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

        @MockBean
        private AppUserRepository appUserRepository;

    @Test
        @WithMockUser(roles = "OPERATOR")
    void searchOrdersReturnsPagedDtoAndAppliesFilters() throws Exception {
        Order order = new Order();
        order.setId(17L);
        order.setUserId(42L);
        order.setItemName("Field recorder");
        order.setQuantity(2);
        order.setTotalAmount(new BigDecimal("125.50"));
        order.setStatus(Order.OrderStatus.PAID);
        order.setCreatedAt(Instant.parse("2026-09-29T12:00:00Z"));
        when(orderService.searchOrders(eq(Order.OrderStatus.PAID), eq(42L), eq("recorder"),
                eq(1), eq(10), eq("createdAt"), eq(Sort.Direction.ASC)))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(1, 10), 11));

        mockMvc.perform(get("/api/orders")
                        .param("status", "PAID")
                        .param("userId", "42")
                        .param("itemName", "recorder")
                        .param("page", "1")
                        .param("size", "10")
                        .param("sort", "createdAt")
                        .param("direction", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(17))
                .andExpect(jsonPath("$.content[0].itemName").value("Field recorder"))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(11));

        verify(orderService).searchOrders(Order.OrderStatus.PAID, 42L, "recorder", 1, 10,
                "createdAt", Sort.Direction.ASC);
    }

    @Test
        @WithMockUser(roles = "OPERATOR")
    void searchOrdersRejectsPageSizesAboveTheApiLimit() throws Exception {
        mockMvc.perform(get("/api/orders").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void statisticsEndpointReturnsGlobalSummary() throws Exception {
        when(orderService.getStatistics()).thenReturn(
                new OrderService.OrderStatistics(12, 8, 2, 1, new BigDecimal("318.50")));

        mockMvc.perform(get("/api/orders/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(12))
                .andExpect(jsonPath("$.paidVolume").value(318.5));
    }

        @Test
        void ordersRequireAuthentication() throws Exception {
                mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        }

        @Test
        void malformedBearerTokenIsRejected() throws Exception {
                mockMvc.perform(get("/api/orders").header("Authorization", "Bearer not.a.valid.jwt"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "CUSTOMER")
        void unrecognizedRoleCannotReadOperationalOrders() throws Exception {
                mockMvc.perform(get("/api/orders")).andExpect(status().isForbidden());
        }
}