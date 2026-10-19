package br.com.chiccharmosa.controller;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import br.com.chiccharmosa.assembler.RoleAssembler;
import br.com.chiccharmosa.dto.RoleDto.PermissionResponse;
import br.com.chiccharmosa.dto.RoleDto.RoleRequest;
import br.com.chiccharmosa.dto.RoleDto.RoleResponse;
import br.com.chiccharmosa.service.RoleService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;
    private final RoleAssembler roleAssembler;
    private final PagedResourcesAssembler<RoleResponse> pagedAssembler;

    public RoleController(RoleService roleService, RoleAssembler roleAssembler,
            PagedResourcesAssembler<RoleResponse> pagedAssembler) {
        this.roleService = roleService;
        this.roleAssembler = roleAssembler;
        this.pagedAssembler = pagedAssembler;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('role:manage')")
    public ResponseEntity<EntityModel<RoleResponse>> create(@Valid @RequestBody RoleRequest request) {
        boolean existed = request.id() != null && roleService.exists(request.id());
        EntityModel<RoleResponse> model = roleAssembler.toModel(roleService.create(request, Instant.now()));
        if (existed) {
            return ResponseEntity.ok(model);
        }
        return ResponseEntity.created(model.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(model);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('role:manage')")
    public PagedModel<EntityModel<RoleResponse>> list(Pageable pageable) {
        return pagedAssembler.toModel(roleService.list(pageable), roleAssembler);
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('role:manage')")
    public CollectionModel<PermissionResponse> permissions() {
        return CollectionModel.of(roleService.permissions(),
                linkTo(methodOn(RoleController.class).permissions()).withSelfRel());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('role:manage')")
    public EntityModel<RoleResponse> get(@PathVariable UUID id) {
        return roleAssembler.toModel(roleService.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('role:manage')")
    public EntityModel<RoleResponse> update(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        return roleAssembler.toModel(roleService.update(id, request, Instant.now()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('role:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        roleService.delete(id, Instant.now());
    }
}
