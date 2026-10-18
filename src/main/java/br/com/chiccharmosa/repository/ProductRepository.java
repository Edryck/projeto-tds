package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.Product;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndDeletedAtIsNull(UUID id);

    Page<Product> findByDeletedAtIsNull(Pageable pageable);

    Page<Product> findByDeletedAtIsNullAndNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Product> findByDeletedAtIsNullAndCategoryId(UUID categoryId, Pageable pageable);

    Page<Product> findByDeletedAtIsNullAndCategoryIdAndNameContainingIgnoreCase(UUID categoryId, String name,
            Pageable pageable);

    boolean existsByCategoryIdAndDeletedAtIsNull(UUID categoryId);

    @Query("select e from Product e where e.updatedAt > :since order by e.updatedAt, e.id")
    List<Product> findChangedSince(@Param("since") Instant since, Limit limit);

    @Query("""
            select e from Product e
            where e.updatedAt > :since or (e.updatedAt = :since and e.id > :afterId)
            order by e.updatedAt, e.id
            """)
    List<Product> findChangedAfter(@Param("since") Instant since, @Param("afterId") UUID afterId, Limit limit);
}
