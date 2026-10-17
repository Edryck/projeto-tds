package br.com.chiccharmosa.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import br.com.chiccharmosa.controller.CategoryController;
import br.com.chiccharmosa.dto.CategoryDto.CategoryResponse;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

@Component
public class CategoryAssembler implements RepresentationModelAssembler<CategoryResponse, EntityModel<CategoryResponse>> {

    @Override
    public EntityModel<CategoryResponse> toModel(CategoryResponse category) {
        return EntityModel.of(category,
                linkTo(methodOn(CategoryController.class).get(category.id())).withSelfRel(),
                linkTo(methodOn(CategoryController.class).list(null, null)).withRel("categories"));
    }
}
