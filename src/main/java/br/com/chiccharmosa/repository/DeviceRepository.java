package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.Device;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Optional<Device> findByIdAndUserId(UUID id, UUID userId);

    Page<Device> findByDeletedAtIsNull(Pageable pageable);

    Page<Device> findByDeletedAtIsNullAndUserId(UUID userId, Pageable pageable);

    Optional<Device> findByIdAndDeletedAtIsNull(UUID id);
}
