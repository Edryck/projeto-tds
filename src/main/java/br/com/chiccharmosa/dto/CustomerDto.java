package br.com.chiccharmosa.dto;

import br.com.chiccharmosa.entity.Address;
import br.com.chiccharmosa.entity.Customer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.hateoas.server.core.Relation;

public final class CustomerDto {

    private CustomerDto() {
    }

    public record CustomerRequest(UUID id, @NotBlank @Size(max = 150) String name, @Size(max = 20) String phone,
            @Email @Size(max = 255) String email, @Size(max = 1000) String note) {
    }

    @Relation(collectionRelation = "customers")
    public record CustomerResponse(UUID id, String name, String phone, String email, String note) {

        public static CustomerResponse from(Customer customer) {
            return new CustomerResponse(customer.getId(), customer.getName(), customer.getPhone(),
                    customer.getEmail(), customer.getNote());
        }
    }

    public record AddressRequest(UUID id, @NotBlank @Size(max = 200) String street, @Size(max = 20) String number,
            @Size(max = 100) String complement, @Size(max = 100) String district,
            @NotBlank @Size(max = 100) String city, @NotBlank @Size(min = 2, max = 2) String state,
            @Size(max = 9) String zipCode) {
    }

    @Relation(collectionRelation = "addresses")
    public record AddressResponse(UUID id, UUID customerId, String street, String number, String complement,
            String district, String city, String state, String zipCode) {

        public static AddressResponse from(Address address) {
            return new AddressResponse(address.getId(), address.getCustomerId(), address.getStreet(),
                    address.getNumber(), address.getComplement(), address.getDistrict(), address.getCity(),
                    address.getState(), address.getZipCode());
        }
    }
}
