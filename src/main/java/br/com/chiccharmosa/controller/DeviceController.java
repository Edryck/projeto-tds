package br.com.chiccharmosa.controller;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import br.com.chiccharmosa.dto.UserDto.DeviceResponse;
import br.com.chiccharmosa.service.UserService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final UserService userService;
    private final PagedResourcesAssembler<DeviceResponse> pagedAssembler;

    public DeviceController(UserService userService, PagedResourcesAssembler<DeviceResponse> pagedAssembler) {
        this.userService = userService;
        this.pagedAssembler = pagedAssembler;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:manage')")
    public PagedModel<EntityModel<DeviceResponse>> list(@RequestParam(required = false) UUID userId,
            Pageable pageable) {
        return pagedAssembler.toModel(userService.listDevices(userId, pageable), this::toModel);
    }

    @PatchMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('user:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable UUID id) {
        userService.disableDevice(id, Instant.now());
    }

    private EntityModel<DeviceResponse> toModel(DeviceResponse device) {
        return EntityModel.of(device,
                linkTo(methodOn(DeviceController.class).list(device.userId(), null)).withSelfRel(),
                linkTo(methodOn(UserController.class).get(device.userId())).withRel("user"));
    }
}
