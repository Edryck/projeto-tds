package br.com.chiccharmosa.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import br.com.chiccharmosa.controller.RoleController;
import br.com.chiccharmosa.controller.UserController;
import br.com.chiccharmosa.dto.UserDto.UserResponse;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

@Component
public class UserAssembler implements RepresentationModelAssembler<UserResponse, EntityModel<UserResponse>> {

    @Override
    public EntityModel<UserResponse> toModel(UserResponse user) {
        return EntityModel.of(user,
                linkTo(methodOn(UserController.class).get(user.id())).withSelfRel(),
                linkTo(methodOn(UserController.class).list(null, null)).withRel("users"),
                linkTo(methodOn(RoleController.class).get(user.roleId())).withRel("role"));
    }
}
