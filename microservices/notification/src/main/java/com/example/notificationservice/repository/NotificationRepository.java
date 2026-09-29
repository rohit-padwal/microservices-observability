package com.example.notificationservice.repository;

import com.example.notificationservice.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

/** Database boundary for pageable delivery searches and status counts. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

        @Query("""
                        select n from Notification n
                        where (:status is null or n.status = :status)
                            and (:paymentId is null or n.paymentId = :paymentId)
                            and (:orderId is null or n.orderId = :orderId)
                            and (:type is null or lower(n.type) like lower(concat('%', :type, '%')))
                            and (:minimumAmount is null or n.amount >= :minimumAmount)
                            and (:maximumAmount is null or n.amount <= :maximumAmount)
                        """)
        Page<Notification> search(@Param("status") Notification.Status status,
                                                            @Param("paymentId") Long paymentId,
                                                            @Param("orderId") Long orderId,
                                                            @Param("type") String type,
                                                            @Param("minimumAmount") BigDecimal minimumAmount,
                                                            @Param("maximumAmount") BigDecimal maximumAmount,
                                                            Pageable pageable);

        long countByStatus(Notification.Status status);
}
