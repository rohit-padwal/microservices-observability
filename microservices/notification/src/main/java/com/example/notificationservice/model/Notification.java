package com.example.notificationservice.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** Delivery record owned by Notification Service; related IDs are references, not cross-service JPA relations. */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notifications_order_created_at", columnList = "order_id, created_at"),
    @Index(name = "idx_notifications_payment_created_at", columnList = "payment_id, created_at")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Positive
    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @NotNull
    @Positive
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @NotNull
    @Positive
    @Column(nullable = false)
    private BigDecimal amount;

    @NotBlank
    @Size(max = 80)
    @Column(nullable = false, length = 80)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Notification() {
    }

    private Notification(Builder builder) {
        this.paymentId = builder.paymentId;
        this.orderId = builder.orderId;
        this.amount = builder.amount;
        this.type = builder.type;
        this.status = builder.status;
        this.createdAt = builder.createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long paymentId;
        private Long orderId;
        private BigDecimal amount;
        private String type;
        private Status status;
        private Instant createdAt = Instant.now();

        public Builder paymentId(Long paymentId) {
            this.paymentId = paymentId;
            return this;
        }

        public Builder orderId(Long orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Notification build() {
            return new Notification(this);
        }
    }

    public enum Status {
        SENT, FAILED
    }
}
