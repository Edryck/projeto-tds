package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.Category;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findByIdAndDeletedAtIsNull(UUID id);

    Page<Category> findByDeletedAtIsNull(Pageable pageable);

    Page<Category> findByDeletedAtIsNullAndNameContainingIgnoreCase(String name, Pageable pageable);
}
