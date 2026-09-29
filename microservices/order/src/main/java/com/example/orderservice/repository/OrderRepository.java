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
		/** Optional predicates share one paged query so filter combinations do not require loading all orders. */
		/** Optional filters share one query; Spring Data derives the matching count for stable page navigation. */
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

		/** Status counts feed global dashboard metrics without materializing order entities. */
		long countByStatus(Order.OrderStatus status);

		/** Sum in SQL avoids reading every paid order into application memory for the dashboard volume. */
		@Query("select sum(o.totalAmount) from Order o where o.status = :status")
		BigDecimal sumAmountByStatus(@Param("status") Order.OrderStatus status);
}
