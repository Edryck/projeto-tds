package br.com.chiccharmosa.service;

import br.com.chiccharmosa.dto.RoleDto.PermissionResponse;
import br.com.chiccharmosa.dto.RoleDto.RoleRequest;
import br.com.chiccharmosa.dto.RoleDto.RoleResponse;
import br.com.chiccharmosa.entity.Permission;
import br.com.chiccharmosa.entity.Role;
import br.com.chiccharmosa.exception.BusinessException;
import br.com.chiccharmosa.repository.PermissionRepository;
import br.com.chiccharmosa.repository.RoleRepository;
import br.com.chiccharmosa.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public RoleService(RoleRepository roleRepository, PermissionRepository permissionRepository,
            UserRepository userRepository, AuditService auditService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public boolean exists(UUID id) {
        return roleRepository.existsById(id);
    }

    @Transactional
    public RoleResponse create(RoleRequest request, Instant changedAt) {
        if (request.id() != null) {
            Role existing = roleRepository.findById(request.id()).orElse(null);
            if (existing != null) {
                return RoleResponse.from(existing);
            }
        }
        Role role = new Role();
        role.setId(request.id() == null ? UUID.randomUUID() : request.id());
        role.setName(request.name());
        role.setPermissions(findPermissions(permissionCodesOf(request)));
        role.touch(changedAt);
        RoleResponse response = RoleResponse.from(roleRepository.save(role));
        auditService.record(AuditService.ROLE, role.getId(), AuditService.CREATE, null, response, changedAt);
        return response;
    }

    @Transactional(readOnly = true)
    public RoleResponse get(UUID id) {
        return RoleResponse.from(findActive(id));
    }

    @Transactional(readOnly = true)
    public Page<RoleResponse> list(Pageable pageable) {
        return roleRepository.findByDeletedAtIsNull(pageable).map(RoleResponse::from);
    }

    @Transactional
    public RoleResponse update(UUID id, RoleRequest request, Instant changedAt) {
        Role role = findActive(id);
        RoleResponse before = RoleResponse.from(role);
        role.setName(request.name());
        role.setPermissions(findPermissions(permissionCodesOf(request)));
        role.touch(changedAt);
        RoleResponse after = RoleResponse.from(role);
        auditService.record(AuditService.ROLE, id, AuditService.UPDATE, before, after, changedAt);
        return after;
    }

    @Transactional
    public void delete(UUID id, Instant changedAt) {
        Role role = findActive(id);
        if (userRepository.existsByRoleIdAndDeletedAtIsNull(id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Perfil em uso por usuários");
        }
        RoleResponse before = RoleResponse.from(role);
        role.markDeleted(changedAt);
        auditService.record(AuditService.ROLE, id, AuditService.DELETE, before, null, changedAt);
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> permissions() {
        List<PermissionResponse> responses = new ArrayList<>();
        for (Permission permission : permissionRepository.findAll(Sort.by("code"))) {
            responses.add(PermissionResponse.from(permission));
        }
        return responses;
    }

    private Role findActive(UUID id) {
        return roleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Perfil não encontrado"));
    }

    private Set<String> permissionCodesOf(RoleRequest request) {
        return request.permissionCodes() == null ? Set.of() : request.permissionCodes();
    }

    private Set<Permission> findPermissions(Set<String> codes) {
        Set<Permission> permissions = permissionRepository.findByCodeIn(codes);
        Set<String> found = new HashSet<>();
        for (Permission permission : permissions) {
            found.add(permission.getCode());
        }
        for (String code : codes) {
            if (!found.contains(code)) {
                throw new BusinessException(HttpStatus.valueOf(422), "Permissão inexistente: " + code);
            }
        }
        return permissions;
    }
}
