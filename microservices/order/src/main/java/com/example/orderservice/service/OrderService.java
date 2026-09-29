package com.example.orderservice.service;

import com.example.orderservice.client.PaymentServiceClient;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

/** Owns order persistence and the synchronous order-to-payment business workflow. */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORT_FIELDS = Set.of("id", "createdAt", "totalAmount", "status");

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
        // This demo keeps the local write and payment result in one transaction; production should avoid holding it across network I/O.
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

    /**
     * Runs a bounded database search for the requested order filters.
     * @param page zero-based index; negative values normalize to zero
     * @param size requested page size, clamped to 1..100
     * @param sortField allow-listed persisted property
     * @param direction result ordering
     * @return page of matching orders with total-count metadata
     */
    @Transactional(readOnly = true)
    public Page<Order> searchOrders(Order.OrderStatus status, Long userId, String itemName,
                                    int page, int size, String sortField, Sort.Direction direction) {
        if (!SORT_FIELDS.contains(sortField)) {
            throw new IllegalArgumentException("Unsupported order sort field: " + sortField);
        }
        // Bound page size and allow-list sort properties so API callers cannot request unbounded reads or arbitrary fields.
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(direction, sortField));
        String normalizedItemName = itemName == null || itemName.isBlank() ? null : itemName.trim();
        return orderRepository.search(status, userId, normalizedItemName, pageable);
    }

    /** Aggregates the entire order table in SQL so totals do not depend on current filters or page. */
    @Transactional(readOnly = true)
    public OrderStatistics getStatistics() {
        BigDecimal paidVolume = orderRepository.sumAmountByStatus(Order.OrderStatus.PAID);
        return new OrderStatistics(
                orderRepository.count(),
                orderRepository.countByStatus(Order.OrderStatus.PAID),
                orderRepository.countByStatus(Order.OrderStatus.PAYMENT_FAILED),
                orderRepository.countByStatus(Order.OrderStatus.CANCELLED),
            paidVolume == null ? BigDecimal.ZERO : paidVolume);
    }

    /**
     * @param id persisted order key
     * @return entity or throws OrderNotFoundException for HTTP 404 mapping
     */
    @Transactional(readOnly = true)
    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    /** Applies the cancellation business transition and persists it; payment statuses are not caller-editable. */
    @Transactional
    public void cancelOrder(Long id) {
        updateStatus(id, Order.OrderStatus.CANCELLED);
    }

    /**
     * Applies the supported order status transition.
     * @param status only CANCELLED is accepted; Payment Service owns paid/failed outcomes
     * @return updated order entity for response mapping
     * @throws IllegalArgumentException when a caller requests another status
     */
    @Transactional
    public Order updateStatus(Long id, Order.OrderStatus status) {
        // Payment owns PAID/PAYMENT_FAILED; Order exposes only the customer cancellation transition here.
        if (status != Order.OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Order status can only be changed to CANCELLED through this API");
        }
        Order order = getOrderById(id);
        if (order.getStatus() != Order.OrderStatus.CANCELLED) {
            order.setStatus(Order.OrderStatus.CANCELLED);
            order = orderRepository.save(order);
        }
        log.info("Order cancelled id={}", id);
        return order;
    }

    public record OrderStatistics(long totalOrders, long paidOrders, long failedPayments,
                                  long cancelledOrders, BigDecimal paidVolume) {}
}
