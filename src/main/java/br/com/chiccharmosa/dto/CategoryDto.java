package br.com.chiccharmosa.dto;

import br.com.chiccharmosa.entity.Category;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.hateoas.server.core.Relation;

public final class CategoryDto {

    private CategoryDto() {
    }

    public record CategoryRequest(UUID id, @NotBlank String name) {
    }

    @Relation(collectionRelation = "categories")
    public record CategoryResponse(UUID id, String name) {

        public static CategoryResponse from(Category category) {
            return new CategoryResponse(category.getId(), category.getName());
        }
    }
}
