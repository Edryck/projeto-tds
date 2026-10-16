package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByIdAndDeletedAtIsNull(UUID id);

    Page<Role> findByDeletedAtIsNull(Pageable pageable);
}
