package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.CancellationRequest;
import br.com.chiccharmosa.entity.CancellationStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CancellationRequestRepository extends JpaRepository<CancellationRequest, UUID> {

    @EntityGraph(attributePaths = "requestedBy")
    @Query("""
            select c from CancellationRequest c
            where c.order.id = :orderId and c.status = 'PENDING' and c.deletedAt is null
            """)
    Optional<CancellationRequest> findPendingByOrderId(@Param("orderId") UUID orderId);

    @EntityGraph(attributePaths = "requestedBy")
    @Query("""
            select c from CancellationRequest c
            where c.order.id in :orderIds and c.status = 'PENDING' and c.deletedAt is null
            """)
    List<CancellationRequest> findPendingByOrderIds(@Param("orderIds") Collection<UUID> orderIds);

    @Query("select c.order.id from CancellationRequest c where c.id = :id and c.deletedAt is null")
    Optional<UUID> findOrderIdById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"order", "order.customer", "requestedBy", "decidedBy"})
    @Query("""
            select c from CancellationRequest c
            where c.deletedAt is null
            and (:status is null or c.status = :status)
            order by c.createdAt desc
            """)
    Page<CancellationRequest> search(@Param("status") CancellationStatus status, Pageable pageable);
}
