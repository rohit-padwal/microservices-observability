package com.example.orderservice.repository;

import com.example.orderservice.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void clearOrders() {
        orderRepository.deleteAll();
    }

    @Test
    void searchFiltersAndPagesOrdersAndCalculatesPaidStatistics() {
        orderRepository.save(order(10L, "Field recorder", Order.OrderStatus.PAID, "120.00"));
        orderRepository.save(order(10L, "Recorder case", Order.OrderStatus.PAID, "35.50"));
        orderRepository.save(order(11L, "Field recorder", Order.OrderStatus.PAYMENT_FAILED, "120.00"));

        Page<Order> result = orderRepository.search(Order.OrderStatus.PAID, 10L, "recorder",
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertEquals(2, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals("Recorder case", result.getContent().getFirst().getItemName());
        assertEquals(2, orderRepository.countByStatus(Order.OrderStatus.PAID));
        assertEquals(new BigDecimal("155.50"), orderRepository.sumAmountByStatus(Order.OrderStatus.PAID));
    }

    private Order order(Long userId, String itemName, Order.OrderStatus status, String amount) {
        Order order = new Order();
        order.setUserId(userId);
        order.setItemName(itemName);
        order.setQuantity(1);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal(amount));
        return order;
    }
}