package br.com.chiccharmosa.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import br.com.chiccharmosa.controller.RoleController;
import br.com.chiccharmosa.dto.RoleDto.RoleResponse;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

@Component
public class RoleAssembler implements RepresentationModelAssembler<RoleResponse, EntityModel<RoleResponse>> {

    @Override
    public EntityModel<RoleResponse> toModel(RoleResponse role) {
        return EntityModel.of(role,
                linkTo(methodOn(RoleController.class).get(role.id())).withSelfRel(),
                linkTo(methodOn(RoleController.class).list(null)).withRel("roles"),
                linkTo(methodOn(RoleController.class).permissions()).withRel("permissions"));
    }
}
