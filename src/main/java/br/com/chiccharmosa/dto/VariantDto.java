package br.com.chiccharmosa.dto;

import br.com.chiccharmosa.entity.ProductVariant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.hateoas.server.core.Relation;

public final class VariantDto {

    private VariantDto() {
    }

    public record VariantRequest(UUID id, @NotBlank String color, @NotBlank String size, String model,
            @NotNull @PositiveOrZero BigDecimal price, @NotNull @PositiveOrZero BigDecimal cost) {
    }

    @Relation(collectionRelation = "variants")
    public record VariantResponse(UUID id, UUID productId, String color, String size, String model,
            BigDecimal price, BigDecimal cost, int balance, String status) {

        public static VariantResponse from(ProductVariant variant, int balance) {
            String status = balance <= 0 ? "SOLD_OUT" : "AVAILABLE";
            return new VariantResponse(variant.getId(), variant.getProduct().getId(), variant.getColor(),
                    variant.getSize(), variant.getModel(), variant.getPrice(), variant.getCost(), balance, status);
        }
    }
}
