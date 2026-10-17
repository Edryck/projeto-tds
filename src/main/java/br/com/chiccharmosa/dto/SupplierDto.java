package br.com.chiccharmosa.dto;

import br.com.chiccharmosa.entity.Supplier;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.hateoas.server.core.Relation;

public final class SupplierDto {

    private SupplierDto() {
    }

    public record SupplierRequest(UUID id, @NotBlank String name, String phone) {
    }

    @Relation(collectionRelation = "suppliers")
    public record SupplierResponse(UUID id, String name, String phone) {

        public static SupplierResponse from(Supplier supplier) {
            return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getPhone());
        }
    }
}
