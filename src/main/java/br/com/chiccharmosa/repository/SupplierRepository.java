package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.Supplier;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Optional<Supplier> findByIdAndDeletedAtIsNull(UUID id);

    Page<Supplier> findByDeletedAtIsNull(Pageable pageable);

    Page<Supplier> findByDeletedAtIsNullAndNameContainingIgnoreCase(String name, Pageable pageable);
}
