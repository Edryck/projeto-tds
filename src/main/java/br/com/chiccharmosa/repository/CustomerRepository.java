package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByIdAndDeletedAtIsNull(UUID id);

    Page<Customer> findByDeletedAtIsNull(Pageable pageable);

    Page<Customer> findByDeletedAtIsNullAndNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Customer> findByDeletedAtIsNullAndPhoneContaining(String phone, Pageable pageable);
}
