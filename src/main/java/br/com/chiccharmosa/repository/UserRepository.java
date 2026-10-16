package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    Page<User> findByDeletedAtIsNull(Pageable pageable);

    Page<User> findByDeletedAtIsNullAndNameContainingIgnoreCase(String name, Pageable pageable);

    boolean existsByRoleIdAndDeletedAtIsNull(UUID roleId);
}
