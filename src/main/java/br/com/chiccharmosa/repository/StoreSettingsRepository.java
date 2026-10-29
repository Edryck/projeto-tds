package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.StoreSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreSettingsRepository extends JpaRepository<StoreSettings, UUID> {
}
