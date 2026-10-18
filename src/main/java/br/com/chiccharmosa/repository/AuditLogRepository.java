package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.AuditLog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByEntityIdAndAction(UUID entityId, String action);

    @Query("""
            select a from AuditLog a
            where (:entity is null or a.entity = :entity)
            and (:entityId is null or a.entityId = :entityId)
            and (:userId is null or a.userId = :userId)
            and (:action is null or a.action = :action)
            and a.actedAt >= :from
            and a.actedAt <= :to
            """)
    Page<AuditLog> search(@Param("entity") String entity, @Param("entityId") UUID entityId,
            @Param("userId") UUID userId, @Param("action") String action, @Param("from") Instant from,
            @Param("to") Instant to, Pageable pageable);
}
