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

    /**
     * Creates an order and starts the payment workflow.
     * @param request validated business fields; the caller cannot choose ID, status, or timestamps
     * @return {@code 201 Created} with the persisted order DTO
     */
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

    /**
     * Searches orders in the database and returns a stable page DTO.
     * @param page zero-based page index; defaults to zero
     * @param size requested rows per page, bounded to 1..100
     * @param sort allow-listed entity property; arbitrary property names are rejected
     * @return matching order slice plus total page/row counts
     */
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

    /** Returns global counts and paid volume, not values limited to the current search page. */
    @GetMapping("/statistics")
    public OrderService.OrderStatistics getStatistics() {
        return orderService.getStatistics();
    }

    /** @param id persisted order identifier; missing records become 404 responses */
    @GetMapping("/{id}")
    public OrderResponse getOrderById(@PathVariable Long id) {
        return OrderResponse.from(orderService.getOrderById(id));
    }

    /** Cancels an order and returns 204; the server permits this mutation only to ADMIN. */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long id) {
        orderService.cancelOrder(id);
        return ResponseEntity.noContent().build();
    }

    /** Requests the cancellation transition; payment-owned terminal outcomes cannot be set by this endpoint. */
    @PatchMapping("/{id}/status")
    public OrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return OrderResponse.from(orderService.updateStatus(id, request.status()));
    }

    /** Client-owned order fields only; positive IDs/counts/amounts and a nonblank name are required. */
    public record CreateOrderRequest(
            @jakarta.validation.constraints.NotNull @Positive Long userId,
            @jakarta.validation.constraints.NotBlank @Size(max = 255) String itemName,
            @jakarta.validation.constraints.NotNull @Positive Integer quantity,
            @jakarta.validation.constraints.NotNull @Positive BigDecimal totalAmount) {}

    /** Request body for status changes; service logic accepts only CANCELLED from the persisted status enum. */
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
