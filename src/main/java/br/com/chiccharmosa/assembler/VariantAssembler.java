package br.com.chiccharmosa.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import br.com.chiccharmosa.controller.ProductController;
import br.com.chiccharmosa.controller.VariantController;
import br.com.chiccharmosa.dto.VariantDto.VariantResponse;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class VariantAssembler implements RepresentationModelAssembler<VariantResponse, EntityModel<VariantResponse>> {

    @Override
    public EntityModel<VariantResponse> toModel(VariantResponse variant) {
        EntityModel<VariantResponse> model = EntityModel.of(variant,
                linkTo(methodOn(VariantController.class).get(variant.id())).withSelfRel(),
                linkTo(methodOn(ProductController.class).get(variant.productId())).withRel("product"),
                linkTo(VariantController.class).slash("variants").slash(variant.id()).slash("movements")
                        .withRel("movements"));
        if (canAdjustStock()) {
            model.add(linkTo(VariantController.class).slash("variants").slash(variant.id()).slash("adjustments")
                    .withRel("adjust"));
        }
        return model;
    }

    private boolean canAdjustStock() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("stock:adjust"));
    }
}
