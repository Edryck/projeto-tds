package br.com.chiccharmosa.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

class JwtServiceTest {

    private static final String SECRET = "test-secret-0123456789-0123456789-0123456789";

    @Test
    void parsesTokenItCreated() {
        JwtService service = new JwtService(SECRET, 900);
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        JwtService.JwtClaims claims = service.parse(service.createAccessToken(userId, deviceId));

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.deviceId()).isEqualTo(deviceId);
    }

    @Test
    void rejectsTamperedToken() {
        JwtService service = new JwtService(SECRET, 900);
        String token = service.createAccessToken(UUID.randomUUID(), UUID.randomUUID());
        int firstDot = token.indexOf('.');
        int secondDot = token.indexOf('.', firstDot + 1);
        int middle = firstDot + (secondDot - firstDot) / 2;
        char original = token.charAt(middle);
        String tampered = token.substring(0, middle) + (original == 'A' ? 'B' : 'A') + token.substring(middle + 1);

        assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService service = new JwtService(SECRET, -1);
        String token = service.createAccessToken(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(BadCredentialsException.class);
    }
}
