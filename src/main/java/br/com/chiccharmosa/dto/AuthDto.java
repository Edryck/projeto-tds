package br.com.chiccharmosa.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

public final class AuthDto {

    private AuthDto() {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            @NotNull UUID deviceId,
            String deviceName) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds) {
    }

    public record MeResponse(UUID id, String name, String email, String role, Set<String> permissions) {
    }
}
