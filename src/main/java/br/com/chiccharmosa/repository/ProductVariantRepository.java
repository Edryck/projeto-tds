package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.ProductVariant;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
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

    List<ProductVariant> findByProductIdInAndDeletedAtIsNull(Collection<UUID> productIds);

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

    @Query(value = """
            select v, coalesce(sum(m.quantity), 0) from ProductVariant v
            join v.product p
            left join StockMovement m on m.variant = v
            where v.deletedAt is null and p.deletedAt is null
            and (lower(p.name) like lower(concat('%', :q, '%'))
                or lower(v.color) like lower(concat('%', :q, '%'))
                or lower(v.size) like lower(concat('%', :q, '%'))
                or lower(v.model) like lower(concat('%', :q, '%')))
            group by v, p.name
            having (:minBalance is null or coalesce(sum(m.quantity), 0) >= :minBalance)
            and (:maxBalance is null or coalesce(sum(m.quantity), 0) <= :maxBalance)
            order by p.name, v.size, v.color, v.id
            """, countQuery = """
            select count(v) from ProductVariant v
            join v.product p
            where v.deletedAt is null and p.deletedAt is null
            and (lower(p.name) like lower(concat('%', :q, '%'))
                or lower(v.color) like lower(concat('%', :q, '%'))
                or lower(v.size) like lower(concat('%', :q, '%'))
                or lower(v.model) like lower(concat('%', :q, '%')))
            and (:minBalance is null
                or coalesce((select sum(m.quantity) from StockMovement m where m.variant = v), 0) >= :minBalance)
            and (:maxBalance is null
                or coalesce((select sum(m.quantity) from StockMovement m where m.variant = v), 0) <= :maxBalance)
            """)
    Page<Object[]> search(@Param("q") String q, @Param("minBalance") Integer minBalance,
            @Param("maxBalance") Integer maxBalance, Pageable pageable);
}
