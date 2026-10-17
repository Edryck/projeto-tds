package br.com.chiccharmosa.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import br.com.chiccharmosa.controller.SupplierController;
import br.com.chiccharmosa.dto.SupplierDto.SupplierResponse;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

@Component
public class SupplierAssembler implements RepresentationModelAssembler<SupplierResponse, EntityModel<SupplierResponse>> {

    @Override
    public EntityModel<SupplierResponse> toModel(SupplierResponse supplier) {
        return EntityModel.of(supplier,
                linkTo(methodOn(SupplierController.class).get(supplier.id())).withSelfRel(),
                linkTo(methodOn(SupplierController.class).list(null, null)).withRel("suppliers"));
    }
}
