package com.example.orderservice.service;

import com.example.orderservice.client.PaymentServiceClient;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final PaymentServiceClient paymentServiceClient;

    public OrderService(OrderRepository orderRepository, PaymentServiceClient paymentServiceClient) {
        this.orderRepository = orderRepository;
        this.paymentServiceClient = paymentServiceClient;
    }

    /**
     * Creates an order end-to-end:
     *  1. Persist the order as CREATED
     *  2. Request payment (call to payment-service, which in turn calls
     *     fraud-service and notification-service)
     *  3. Update order status based on the payment outcome
     * This fan-out is what shows up as a multi-service trace in Tempo:
     * gateway -> order -> payment -> fraud -> db -> notification.
     */
    @Transactional
    public Order createOrder(Order order) {
        MDC.put("user_id", String.valueOf(order.getUserId()));
        try {
            order.setStatus(Order.OrderStatus.CREATED);
            Order saved = orderRepository.save(order);
            MDC.put("order_id", String.valueOf(saved.getId()));
            log.info("Order created amount={}", saved.getTotalAmount());

            PaymentServiceClient.PaymentResult result =
                    paymentServiceClient.requestPayment(saved.getId(), saved.getTotalAmount());

            saved.setStatus(result.success() ? Order.OrderStatus.PAID : Order.OrderStatus.PAYMENT_FAILED);
            Order updated = orderRepository.save(saved);

            if (result.success()) {
                log.info("Order paid payment_id={}", result.paymentId());
            } else {
                MDC.put("error_code", "PAYMENT_DECLINED");
                log.warn("Order payment failed");
            }
            return updated;
        } finally {
            MDC.remove("user_id");
            MDC.remove("order_id");
            MDC.remove("error_code");
        }
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional
    public void cancelOrder(Long id) {
        Order order = getOrderById(id);
        order.setStatus(Order.OrderStatus.CANCELLED);
        orderRepository.save(order);
        log.info("Order cancelled id={}", id);
    }
}
