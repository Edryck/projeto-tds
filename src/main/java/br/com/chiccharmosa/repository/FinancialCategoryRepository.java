package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.FinancialCategory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialCategoryRepository extends JpaRepository<FinancialCategory, UUID> {

    Optional<FinancialCategory> findByIdAndDeletedAtIsNull(UUID id);

    Page<FinancialCategory> findByDeletedAtIsNull(Pageable pageable);
}
