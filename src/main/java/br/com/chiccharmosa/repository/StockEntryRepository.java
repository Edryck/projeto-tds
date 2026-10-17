package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.StockEntry;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockEntryRepository extends JpaRepository<StockEntry, UUID> {

    Optional<StockEntry> findByIdAndDeletedAtIsNull(UUID id);

    Page<StockEntry> findByDeletedAtIsNull(Pageable pageable);
}
