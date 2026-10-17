package br.com.chiccharmosa.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import br.com.chiccharmosa.controller.CategoryController;
import br.com.chiccharmosa.controller.ProductController;
import br.com.chiccharmosa.controller.VariantController;
import br.com.chiccharmosa.dto.ProductDto.ProductResponse;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

@Component
public class ProductAssembler implements RepresentationModelAssembler<ProductResponse, EntityModel<ProductResponse>> {

    @Override
    public EntityModel<ProductResponse> toModel(ProductResponse product) {
        return EntityModel.of(product,
                linkTo(methodOn(ProductController.class).get(product.id())).withSelfRel(),
                linkTo(methodOn(ProductController.class).list(null, null, null)).withRel("products"),
                linkTo(methodOn(CategoryController.class).get(product.categoryId())).withRel("category"),
                linkTo(methodOn(VariantController.class).list(product.id(), null)).withRel("variants"));
    }
}
