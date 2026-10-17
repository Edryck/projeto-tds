package br.com.chiccharmosa.dto;

import br.com.chiccharmosa.entity.Category;
import br.com.chiccharmosa.entity.Product;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.hateoas.server.core.Relation;

public final class ProductDto {

    private ProductDto() {
    }

    public record ProductRequest(UUID id, @NotNull UUID categoryId, @NotBlank String name, String description) {
    }

    @Relation(collectionRelation = "products")
    public record ProductResponse(UUID id, UUID categoryId, String categoryName, String name, String description) {

        public static ProductResponse from(Product product) {
            Category category = product.getCategory();
            return new ProductResponse(product.getId(), category.getId(), category.getName(),
                    product.getName(), product.getDescription());
        }
    }
}
