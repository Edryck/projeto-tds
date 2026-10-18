package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.CashClosing;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashClosingRepository extends JpaRepository<CashClosing, UUID> {

    boolean existsByAccountIdAndClosingDate(UUID accountId, LocalDate closingDate);

    Optional<CashClosing> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            select c from CashClosing c
            where c.deletedAt is null
            and (:accountId is null or c.accountId = :accountId)
            and c.closingDate >= :from
            and c.closingDate <= :to
            """)
    Page<CashClosing> search(@Param("accountId") UUID accountId, @Param("from") LocalDate from,
            @Param("to") LocalDate to, Pageable pageable);
}
