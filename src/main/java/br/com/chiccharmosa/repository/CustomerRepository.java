package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.Customer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByIdAndDeletedAtIsNull(UUID id);

    Page<Customer> findByDeletedAtIsNullAndIdNot(UUID excludedId, Pageable pageable);

    Page<Customer> findByDeletedAtIsNullAndIdNotAndNameContainingIgnoreCase(UUID excludedId, String name,
            Pageable pageable);

    Page<Customer> findByDeletedAtIsNullAndIdNotAndPhoneContaining(UUID excludedId, String phone,
            Pageable pageable);

    @Query("""
            select c from Customer c
            where c.deletedAt is null and c.id <> :excludedId
            and (lower(c.name) like concat('%', :q, '%')
                or lower(c.phone) like concat('%', :q, '%')
                or lower(c.email) like concat('%', :q, '%'))
            """)
    Page<Customer> search(@Param("q") String q, @Param("excludedId") UUID excludedId, Pageable pageable);

    @Query("select e from Customer e where e.updatedAt > :since order by e.updatedAt, e.id")
    List<Customer> findChangedSince(@Param("since") Instant since, Limit limit);

    @Query("""
            select e from Customer e
            where e.updatedAt > :since or (e.updatedAt = :since and e.id > :afterId)
            order by e.updatedAt, e.id
            """)
    List<Customer> findChangedAfter(@Param("since") Instant since, @Param("afterId") UUID afterId, Limit limit);
}
