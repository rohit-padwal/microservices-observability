package com.example.fraudservice.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "fraud_checks")
public class FraudCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "risk_score", nullable = false)
    private double riskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Decision decision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public FraudCheck() {
    }

    private FraudCheck(Builder builder) {
        this.orderId = builder.orderId;
        this.paymentId = builder.paymentId;
        this.amount = builder.amount;
        this.riskScore = builder.riskScore;
        this.decision = builder.decision;
        this.createdAt = builder.createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public Decision getDecision() {
        return decision;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long orderId;
        private Long paymentId;
        private BigDecimal amount;
        private double riskScore;
        private Decision decision;
        private Instant createdAt = Instant.now();

        public Builder orderId(Long orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder paymentId(Long paymentId) {
            this.paymentId = paymentId;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder riskScore(double riskScore) {
            this.riskScore = riskScore;
            return this;
        }

        public Builder decision(Decision decision) {
            this.decision = decision;
            return this;
        }

        public FraudCheck build() {
            return new FraudCheck(this);
        }
    }

    public enum Decision {
        APPROVE, REVIEW, DECLINE
    }
}
