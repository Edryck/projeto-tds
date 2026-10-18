package br.com.chiccharmosa.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import br.com.chiccharmosa.controller.CashClosingController;
import br.com.chiccharmosa.dto.FinanceDto.CashClosingResponse;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

@Component
public class CashClosingAssembler
        implements RepresentationModelAssembler<CashClosingResponse, EntityModel<CashClosingResponse>> {

    @Override
    public EntityModel<CashClosingResponse> toModel(CashClosingResponse closing) {
        return EntityModel.of(closing,
                linkTo(methodOn(CashClosingController.class).get(closing.id())).withSelfRel(),
                linkTo(methodOn(CashClosingController.class).list(null, null, null, null))
                        .withRel("cash-closings"));
    }
}
