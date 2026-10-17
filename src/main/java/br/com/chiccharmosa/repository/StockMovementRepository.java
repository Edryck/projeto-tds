package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.MovementType;
import br.com.chiccharmosa.entity.StockMovement;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    @Query("select m.variant.id, sum(m.quantity) from StockMovement m where m.variant.id in :ids group by m.variant.id")
    List<Object[]> sumByVariantIds(@Param("ids") Collection<UUID> ids);

    long countByReferenceIdAndType(UUID referenceId, MovementType type);

    List<StockMovement> findByReferenceIdAndType(UUID referenceId, MovementType type);

    Page<StockMovement> findByVariantIdOrderByOccurredAtDesc(UUID variantId, Pageable pageable);
}
