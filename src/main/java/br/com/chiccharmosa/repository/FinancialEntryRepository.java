package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.EntryType;
import br.com.chiccharmosa.entity.FinancialEntry;
import br.com.chiccharmosa.entity.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinancialEntryRepository extends JpaRepository<FinancialEntry, UUID> {

    Optional<FinancialEntry> findByIdAndDeletedAtIsNull(UUID id);

    List<FinancialEntry> findByOrderId(UUID orderId);

    @Query("""
            select e from FinancialEntry e
            where e.deletedAt is null
            and (:accountId is null or e.accountId = :accountId)
            and (:type is null or e.type = :type)
            and e.entryDate >= :from
            and e.entryDate <= :to
            """)
    Page<FinancialEntry> search(@Param("accountId") UUID accountId, @Param("type") EntryType type,
            @Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);

    @Query("""
            select coalesce(sum(e.amount), 0) from FinancialEntry e
            where e.deletedAt is null
            and e.accountId = :accountId
            and e.entryDate = :day
            and e.type = :type
            and (:method is null or e.paymentMethod = :method)
            """)
    BigDecimal sumOfDay(@Param("accountId") UUID accountId, @Param("day") LocalDate day,
            @Param("type") EntryType type, @Param("method") PaymentMethod method);

    @Query("""
            select coalesce(sum(e.amount), 0) from FinancialEntry e
            where e.deletedAt is null
            and e.accountId = :accountId
            and e.entryDate >= :from
            and e.entryDate <= :to
            and e.type = :type
            and e.categoryId not in :ignoredCategories
            """)
    BigDecimal sumOfPeriod(@Param("accountId") UUID accountId, @Param("type") EntryType type,
            @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("ignoredCategories") List<UUID> ignoredCategories);

    @Query("select e from FinancialEntry e where e.updatedAt > :since order by e.updatedAt, e.id")
    List<FinancialEntry> findChangedSince(@Param("since") Instant since, Limit limit);

    @Query("""
            select e from FinancialEntry e
            where e.updatedAt > :since or (e.updatedAt = :since and e.id > :afterId)
            order by e.updatedAt, e.id
            """)
    List<FinancialEntry> findChangedAfter(@Param("since") Instant since, @Param("afterId") UUID afterId, Limit limit);
}
