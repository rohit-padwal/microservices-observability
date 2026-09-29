package com.example.paymentservice.repository;

import com.example.paymentservice.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

/** SQL/JPA query boundary for bounded payment search and aggregate reporting. */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

        @Query("""
                        select p from Payment p
                        where (:status is null or p.status = :status)
                            and (:orderId is null or p.orderId = :orderId)
                            and (:minimumAmount is null or p.amount >= :minimumAmount)
                            and (:maximumAmount is null or p.amount <= :maximumAmount)
                        """)
        Page<Payment> search(@Param("status") Payment.PaymentStatus status,
                                                 @Param("orderId") Long orderId,
                                                 @Param("minimumAmount") BigDecimal minimumAmount,
                                                 @Param("maximumAmount") BigDecimal maximumAmount,
                                                 Pageable pageable);

        long countByStatus(Payment.PaymentStatus status);

        @Query("select sum(p.amount) from Payment p where p.status = :status")
        BigDecimal sumAmountByStatus(@Param("status") Payment.PaymentStatus status);
}
