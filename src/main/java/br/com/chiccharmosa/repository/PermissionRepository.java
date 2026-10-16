package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.Permission;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Set<Permission> findByCodeIn(Collection<String> codes);

    @Query("select p.code from User u join u.role r join r.permissions p where u.id = :userId")
    Set<String> findCodesByUserId(@Param("userId") UUID userId);
}
