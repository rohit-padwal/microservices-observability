package com.example.orderservice.repository;

import com.example.orderservice.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

/** Database boundary for order CRUD, filtered pages, and dashboard aggregates. */
public interface OrderRepository extends JpaRepository<Order, Long> {

		// Keep filtering, sorting, and page counts in SQL instead of loading the full table into memory.
		@Query("""
						select o from Order o
						where (:status is null or o.status = :status)
							and (:userId is null or o.userId = :userId)
							and (:itemName is null or lower(o.itemName) like lower(concat('%', :itemName, '%')))
						""")
		Page<Order> search(@Param("status") Order.OrderStatus status,
											 @Param("userId") Long userId,
											 @Param("itemName") String itemName,
											 Pageable pageable);

		long countByStatus(Order.OrderStatus status);

		@Query("select sum(o.totalAmount) from Order o where o.status = :status")
		BigDecimal sumAmountByStatus(@Param("status") Order.OrderStatus status);
}
