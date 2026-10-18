package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.StockEntry;
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

public interface StockEntryRepository extends JpaRepository<StockEntry, UUID> {

    Optional<StockEntry> findByIdAndDeletedAtIsNull(UUID id);

    Page<StockEntry> findByDeletedAtIsNull(Pageable pageable);

    @Query("select e from StockEntry e where e.updatedAt > :since order by e.updatedAt, e.id")
    List<StockEntry> findChangedSince(@Param("since") Instant since, Limit limit);

    @Query("""
            select e from StockEntry e
            where e.updatedAt > :since or (e.updatedAt = :since and e.id > :afterId)
            order by e.updatedAt, e.id
            """)
    List<StockEntry> findChangedAfter(@Param("since") Instant since, @Param("afterId") UUID afterId, Limit limit);
}
