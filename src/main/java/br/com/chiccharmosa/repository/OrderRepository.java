package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.Order;
import br.com.chiccharmosa.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdAndDeletedAtIsNull(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findLockedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = "customer")
    @Query("""
            select o from Order o
            where o.deletedAt is null
            and (:status is null or o.status = :status)
            and (:customerId is null or o.customer.id = :customerId)
            and o.orderedAt >= :from
            and o.orderedAt <= :to
            """)
    Page<Order> search(@Param("status") OrderStatus status, @Param("customerId") UUID customerId,
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("select e from Order e where e.updatedAt > :since order by e.updatedAt, e.id")
    List<Order> findChangedSince(@Param("since") Instant since, Limit limit);

    @Query("""
            select e from Order e
            where e.updatedAt > :since or (e.updatedAt = :since and e.id > :afterId)
            order by e.updatedAt, e.id
            """)
    List<Order> findChangedAfter(@Param("since") Instant since, @Param("afterId") UUID afterId, Limit limit);
}
