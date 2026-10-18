package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.FinancialAccount;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, UUID> {

    List<FinancialAccount> findByDeletedAtIsNullOrderByName();

    boolean existsByIdAndDeletedAtIsNull(UUID id);
}
