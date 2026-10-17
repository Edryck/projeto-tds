package br.com.chiccharmosa.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import br.com.chiccharmosa.controller.StockController;
import br.com.chiccharmosa.controller.VariantController;
import br.com.chiccharmosa.dto.StockDto.MovementResponse;
import br.com.chiccharmosa.dto.StockDto.StockEntryResponse;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

@Component
public class StockAssembler implements RepresentationModelAssembler<StockEntryResponse, EntityModel<StockEntryResponse>> {

    @Override
    public EntityModel<StockEntryResponse> toModel(StockEntryResponse entry) {
        return EntityModel.of(entry,
                linkTo(methodOn(StockController.class).getEntry(entry.id())).withSelfRel(),
                linkTo(methodOn(StockController.class).listEntries(null)).withRel("stockEntries"));
    }

    public EntityModel<MovementResponse> toMovementModel(MovementResponse movement) {
        return EntityModel.of(movement,
                linkTo(methodOn(VariantController.class).get(movement.variantId())).withRel("variant"),
                linkTo(methodOn(StockController.class).listMovements(movement.variantId(), null))
                        .withRel("movements"));
    }
}
