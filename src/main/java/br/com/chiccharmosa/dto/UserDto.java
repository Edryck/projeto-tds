package br.com.chiccharmosa.dto;

import br.com.chiccharmosa.entity.Device;
import br.com.chiccharmosa.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.hateoas.server.core.Relation;

public final class UserDto {

    private UserDto() {
    }

    public record UserRequest(
            UUID id,
            @NotBlank @Email String email,
            @Size(min = 8) String password,
            @NotBlank String name,
            @NotNull UUID roleId,
            boolean active) {
    }

    @Relation(collectionRelation = "users")
    public record UserResponse(UUID id, String email, String name, UUID roleId, String roleName, boolean active) {

        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole().getId(),
                    user.getRole().getName(), user.isActive());
        }
    }

    @Relation(collectionRelation = "devices")
    public record DeviceResponse(UUID id, UUID userId, String name, boolean active) {

        public static DeviceResponse from(Device device) {
            return new DeviceResponse(device.getId(), device.getUser().getId(), device.getName(), device.isActive());
        }
    }
}
