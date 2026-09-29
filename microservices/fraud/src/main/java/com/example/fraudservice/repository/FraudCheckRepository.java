package com.example.fraudservice.repository;

import com.example.fraudservice.model.FraudCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

/** Database boundary for bounded fraud-check search and risk-decision aggregates. */
public interface FraudCheckRepository extends JpaRepository<FraudCheck, Long> {

        @Query("""
                        select f from FraudCheck f
                        where (:decision is null or f.decision = :decision)
                            and (:orderId is null or f.orderId = :orderId)
                            and (:paymentId is null or f.paymentId = :paymentId)
                            and (:minimumAmount is null or f.amount >= :minimumAmount)
                            and (:maximumAmount is null or f.amount <= :maximumAmount)
                        """)
        Page<FraudCheck> search(@Param("decision") FraudCheck.Decision decision,
                                                        @Param("orderId") Long orderId,
                                                        @Param("paymentId") Long paymentId,
                                                        @Param("minimumAmount") BigDecimal minimumAmount,
                                                        @Param("maximumAmount") BigDecimal maximumAmount,
                                                        Pageable pageable);

        long countByDecision(FraudCheck.Decision decision);

        @Query("select avg(f.riskScore) from FraudCheck f")
        Double averageRiskScore();
}
