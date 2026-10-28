package br.com.chiccharmosa.dto;

import br.com.chiccharmosa.entity.Supplier;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.hateoas.server.core.Relation;

public final class SupplierDto {

    private SupplierDto() {
    }

    public record SupplierRequest(UUID id,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 20) String phone,
            @Size(max = 150) String contactName,
            @Email @Size(max = 255) String email,
            @Size(max = 300) String address,
            @Size(max = 1000) String note) {
    }

    @Relation(collectionRelation = "suppliers")
    public record SupplierResponse(UUID id, String name, String phone, String contactName, String email,
            String address, String note) {

        public static SupplierResponse from(Supplier supplier) {
            return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getPhone(),
                    supplier.getContactName(), supplier.getEmail(), supplier.getAddress(), supplier.getNote());
        }
    }

    @Relation(collectionRelation = "suppliers")
    public record SupplierView(UUID id, String name, String phone, String contactName, String email,
            String address, String note, long entryCount) {
    }
}
