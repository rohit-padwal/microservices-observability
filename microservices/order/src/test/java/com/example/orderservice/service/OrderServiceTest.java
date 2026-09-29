package com.example.orderservice.service;

import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private com.example.orderservice.client.PaymentServiceClient paymentServiceClient;

    @InjectMocks
    private OrderService orderService;

    @Test
    void searchOrdersCapsPageSizeAndNormalizesBlankFilter() {
        when(orderRepository.search(eq(Order.OrderStatus.PAID), eq(42L), isNull(), any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(3)));

        orderService.searchOrders(Order.OrderStatus.PAID, 42L, "  ", 2, 500,
                "createdAt", Sort.Direction.DESC);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).search(eq(Order.OrderStatus.PAID), eq(42L), isNull(), pageable.capture());
        assertEquals(PageRequest.of(2, 100, Sort.by(Sort.Direction.DESC, "createdAt")), pageable.getValue());
    }

    @Test
    void searchOrdersRejectsUnapprovedSortFields() {
        assertThrows(IllegalArgumentException.class, () -> orderService.searchOrders(
                null, null, null, 0, 20, "userId; delete", Sort.Direction.ASC));
    }

    @Test
    void statisticsCombinesCountsAndPaidVolume() {
        when(orderRepository.count()).thenReturn(12L);
        when(orderRepository.countByStatus(Order.OrderStatus.PAID)).thenReturn(8L);
        when(orderRepository.countByStatus(Order.OrderStatus.PAYMENT_FAILED)).thenReturn(2L);
        when(orderRepository.countByStatus(Order.OrderStatus.CANCELLED)).thenReturn(1L);
        when(orderRepository.sumAmountByStatus(Order.OrderStatus.PAID)).thenReturn(new BigDecimal("318.50"));

        OrderService.OrderStatistics statistics = orderService.getStatistics();

        assertEquals(12L, statistics.totalOrders());
        assertEquals(8L, statistics.paidOrders());
        assertEquals(2L, statistics.failedPayments());
        assertEquals(1L, statistics.cancelledOrders());
        assertEquals(new BigDecimal("318.50"), statistics.paidVolume());
    }
}