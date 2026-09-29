package com.example.orderservice.controller;

import com.example.orderservice.model.Order;
import com.example.orderservice.service.OrderService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Translates validated HTTP DTOs into order-service operations and maps results back to stable response DTOs. */
@RestController
@Validated
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        // The request DTO prevents clients from choosing persistence IDs or payment-owned status fields.
        Order order = new Order();
        order.setUserId(request.userId());
        order.setItemName(request.itemName());
        order.setQuantity(request.quantity());
        order.setTotalAmount(request.totalAmount());
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(orderService.createOrder(order)));
    }

    /** page is zero-based, size is capped at 100, and sort is allow-listed to prevent arbitrary entity-property queries. */
    @GetMapping
    public PageResponse<OrderResponse> searchOrders(
            @RequestParam(required = false) Order.OrderStatus status,
            @RequestParam(required = false) @Positive Long userId,
            @RequestParam(required = false) @Size(max = 100) String itemName,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt")
            @Pattern(regexp = "id|createdAt|totalAmount|status") String sort,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        // Return a stable page DTO rather than exposing Spring Data's internal Page serialization shape.
        Page<OrderResponse> result = orderService.searchOrders(status, userId, itemName, page, size, sort, direction)
            .map(OrderResponse::from);
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
            result.getTotalPages(), result.getTotalElements());
    }

    @GetMapping("/statistics")
    public OrderService.OrderStatistics getStatistics() {
        return orderService.getStatistics();
    }

    @GetMapping("/{id}")
    public OrderResponse getOrderById(@PathVariable Long id) {
        return OrderResponse.from(orderService.getOrderById(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long id) {
        orderService.cancelOrder(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    public OrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return OrderResponse.from(orderService.updateStatus(id, request.status()));
    }

    /** Client-owned order fields only; IDs, status, and timestamps remain controlled by the service/database. */
    public record CreateOrderRequest(
            @jakarta.validation.constraints.NotNull @Positive Long userId,
            @jakarta.validation.constraints.NotBlank @Size(max = 255) String itemName,
            @jakarta.validation.constraints.NotNull @Positive Integer quantity,
            @jakarta.validation.constraints.NotNull @Positive BigDecimal totalAmount) {}

    public record UpdateOrderStatusRequest(@jakarta.validation.constraints.NotNull Order.OrderStatus status) {}

    /** Stable page shape prevents the frontend from depending on Spring Data's internal Page JSON representation. */
    public record PageResponse<T>(List<T> content, int number, int size, int totalPages, long totalElements) {}

    /** Read DTO exposes persisted order data without allowing an input payload to overwrite it. */
    public record OrderResponse(Long id, Long userId, String itemName, Integer quantity,
                                 BigDecimal totalAmount, Order.OrderStatus status, Instant createdAt) {
        static OrderResponse from(Order order) {
            return new OrderResponse(order.getId(), order.getUserId(), order.getItemName(),
                    order.getQuantity(), order.getTotalAmount(), order.getStatus(), order.getCreatedAt());
        }
    }
}
