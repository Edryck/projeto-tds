package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.Address;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    Optional<Address> findByIdAndCustomerIdAndDeletedAtIsNull(UUID id, UUID customerId);

    Optional<Address> findByIdAndCustomerId(UUID id, UUID customerId);

    Page<Address> findByCustomerIdAndDeletedAtIsNull(UUID customerId, Pageable pageable);

    boolean existsByIdAndCustomerId(UUID id, UUID customerId);
}
