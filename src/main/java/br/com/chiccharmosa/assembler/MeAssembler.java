package br.com.chiccharmosa.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import br.com.chiccharmosa.controller.AuthController;
import br.com.chiccharmosa.dto.AuthDto.MeResponse;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

@Component
public class MeAssembler implements RepresentationModelAssembler<MeResponse, EntityModel<MeResponse>> {

    @Override
    public EntityModel<MeResponse> toModel(MeResponse me) {
        return EntityModel.of(me, linkTo(methodOn(AuthController.class).me()).withSelfRel());
    }
}
