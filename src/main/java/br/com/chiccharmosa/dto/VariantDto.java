package br.com.chiccharmosa.dto;

import br.com.chiccharmosa.entity.Product;
import br.com.chiccharmosa.entity.ProductVariant;
import br.com.chiccharmosa.service.StockService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.hateoas.server.core.Relation;

public final class VariantDto {

    private VariantDto() {
    }

    public static String stockStatus(int balance) {
        if (balance <= 0) {
            return "SOLD_OUT";
        }
        if (balance <= StockService.LOW_STOCK_LIMIT) {
            return "LOW";
        }
        return "AVAILABLE";
    }

    public record VariantRequest(UUID id, @NotBlank String color, @NotBlank String size, String model,
            @NotNull @PositiveOrZero BigDecimal price, @NotNull @PositiveOrZero BigDecimal cost) {
    }

    @Relation(collectionRelation = "variants")
    public record VariantResponse(UUID id, UUID productId, String color, String size, String model,
            BigDecimal price, BigDecimal cost, int balance, String status) {

        public static VariantResponse from(ProductVariant variant, int balance) {
            return new VariantResponse(variant.getId(), variant.getProduct().getId(), variant.getColor(),
                    variant.getSize(), variant.getModel(), variant.getPrice(), variant.getCost(), balance,
                    stockStatus(balance));
        }
    }

    @Relation(collectionRelation = "variants")
    public record VariantView(UUID id, UUID productId, String productName, String categoryName, String color,
            String size, String model, BigDecimal price, BigDecimal cost, int balance, String status) {

        public static VariantView from(ProductVariant variant, int balance) {
            Product product = variant.getProduct();
            return new VariantView(variant.getId(), product.getId(), product.getName(),
                    product.getCategory().getName(), variant.getColor(), variant.getSize(), variant.getModel(),
                    variant.getPrice(), variant.getCost(), balance, stockStatus(balance));
        }
    }
}
