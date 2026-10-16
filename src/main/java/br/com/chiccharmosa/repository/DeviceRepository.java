package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.Device;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Optional<Device> findByIdAndUserId(UUID id, UUID userId);
}
