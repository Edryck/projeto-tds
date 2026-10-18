package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.ProductVariant;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    Optional<ProductVariant> findByIdAndDeletedAtIsNull(UUID id);

    Page<ProductVariant> findByProductIdAndDeletedAtIsNull(UUID productId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v where v.id = :id")
    Optional<ProductVariant> findLockedById(@Param("id") UUID id);

    @Query("select e from ProductVariant e where e.updatedAt > :since order by e.updatedAt, e.id")
    List<ProductVariant> findChangedSince(@Param("since") Instant since, Limit limit);

    @Query("""
            select e from ProductVariant e
            where e.updatedAt > :since or (e.updatedAt = :since and e.id > :afterId)
            order by e.updatedAt, e.id
            """)
    List<ProductVariant> findChangedAfter(@Param("since") Instant since, @Param("afterId") UUID afterId, Limit limit);
}
