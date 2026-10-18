package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.SyncOperation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncOperationRepository extends JpaRepository<SyncOperation, UUID> {
}
