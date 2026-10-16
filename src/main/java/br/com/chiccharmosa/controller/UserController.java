package br.com.chiccharmosa.controller;

import br.com.chiccharmosa.assembler.UserAssembler;
import br.com.chiccharmosa.dto.UserDto.UserRequest;
import br.com.chiccharmosa.dto.UserDto.UserResponse;
import br.com.chiccharmosa.service.UserService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserAssembler userAssembler;
    private final PagedResourcesAssembler<UserResponse> pagedAssembler;

    public UserController(UserService userService, UserAssembler userAssembler,
            PagedResourcesAssembler<UserResponse> pagedAssembler) {
        this.userService = userService;
        this.userAssembler = userAssembler;
        this.pagedAssembler = pagedAssembler;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:manage')")
    public ResponseEntity<EntityModel<UserResponse>> create(@Valid @RequestBody UserRequest request) {
        boolean existed = request.id() != null && userService.exists(request.id());
        EntityModel<UserResponse> model = userAssembler.toModel(userService.create(request, Instant.now()));
        if (existed) {
            return ResponseEntity.ok(model);
        }
        return ResponseEntity.created(model.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(model);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:manage')")
    public PagedModel<EntityModel<UserResponse>> list(@RequestParam(required = false) String name,
            Pageable pageable) {
        return pagedAssembler.toModel(userService.list(name, pageable), userAssembler);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    public EntityModel<UserResponse> get(@PathVariable UUID id) {
        return userAssembler.toModel(userService.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    public EntityModel<UserResponse> update(@PathVariable UUID id, @Valid @RequestBody UserRequest request) {
        return userAssembler.toModel(userService.update(id, request, Instant.now()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        userService.delete(id, Instant.now());
    }
}
