package br.com.chiccharmosa.dto;

import br.com.chiccharmosa.entity.Permission;
import br.com.chiccharmosa.entity.Role;
import jakarta.validation.constraints.NotBlank;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.hateoas.server.core.Relation;

public final class RoleDto {

    private RoleDto() {
    }

    public record RoleRequest(UUID id, @NotBlank String name, Set<String> permissionCodes) {
    }

    @Relation(collectionRelation = "roles")
    public record RoleResponse(UUID id, String name, Set<String> permissionCodes) {

        public static RoleResponse from(Role role) {
            Set<String> codes = new HashSet<>();
            for (Permission permission : role.getPermissions()) {
                codes.add(permission.getCode());
            }
            return new RoleResponse(role.getId(), role.getName(), codes);
        }
    }

    public record PermissionResponse(UUID id, String code, String description) {

        public static PermissionResponse from(Permission permission) {
            return new PermissionResponse(permission.getId(), permission.getCode(), permission.getDescription());
        }
    }
}
