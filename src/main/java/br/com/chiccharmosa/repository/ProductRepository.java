package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndDeletedAtIsNull(UUID id);

    Page<Product> findByDeletedAtIsNull(Pageable pageable);

    Page<Product> findByDeletedAtIsNullAndNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Product> findByDeletedAtIsNullAndCategoryId(UUID categoryId, Pageable pageable);

    Page<Product> findByDeletedAtIsNullAndCategoryIdAndNameContainingIgnoreCase(UUID categoryId, String name,
            Pageable pageable);

    boolean existsByCategoryIdAndDeletedAtIsNull(UUID categoryId);
}
