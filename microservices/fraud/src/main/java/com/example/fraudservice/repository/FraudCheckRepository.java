package com.example.fraudservice.repository;

import com.example.fraudservice.model.FraudCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FraudCheckRepository extends JpaRepository<FraudCheck, Long> {
    List<FraudCheck> findByPaymentId(Long paymentId);
    List<FraudCheck> findByOrderId(Long orderId);
}
