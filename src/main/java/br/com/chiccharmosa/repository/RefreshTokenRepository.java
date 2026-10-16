package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :revokedAt where t.user.id = :userId and t.revokedAt is null")
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);

    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :revokedAt where t.device.id = :deviceId and t.revokedAt is null")
    int revokeAllByDeviceId(@Param("deviceId") UUID deviceId, @Param("revokedAt") Instant revokedAt);
}
