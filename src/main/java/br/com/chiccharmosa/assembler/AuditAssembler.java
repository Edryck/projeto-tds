package br.com.chiccharmosa.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import br.com.chiccharmosa.controller.AuditController;
import br.com.chiccharmosa.dto.AuditDto.AuditResponse;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

@Component
public class AuditAssembler implements RepresentationModelAssembler<AuditResponse, EntityModel<AuditResponse>> {

    @Override
    public EntityModel<AuditResponse> toModel(AuditResponse audit) {
        return EntityModel.of(audit,
                linkTo(methodOn(AuditController.class).get(audit.id())).withSelfRel(),
                linkTo(methodOn(AuditController.class).list(null, null, null, null, null, null, null))
                        .withRel("audit-logs"));
    }
}
