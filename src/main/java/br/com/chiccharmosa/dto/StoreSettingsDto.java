package br.com.chiccharmosa.dto;

import br.com.chiccharmosa.entity.StoreSettings;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class StoreSettingsDto {

    private StoreSettingsDto() {
    }

    public record SettingsRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 20) String phone,
            @Email @Size(max = 255) String email,
            @Size(max = 300) String address) {
    }

    public record SettingsResponse(String name, String phone, String email, String address, boolean hasLogo) {

        public static SettingsResponse from(StoreSettings settings) {
            return new SettingsResponse(settings.getName(), settings.getPhone(), settings.getEmail(),
                    settings.getAddress(), settings.getLogo() != null);
        }
    }

    public record Logo(byte[] content, String contentType) {
    }
}
