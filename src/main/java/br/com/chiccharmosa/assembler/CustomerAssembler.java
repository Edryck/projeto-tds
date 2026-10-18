package br.com.chiccharmosa.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import br.com.chiccharmosa.controller.CustomerController;
import br.com.chiccharmosa.dto.CustomerDto.AddressResponse;
import br.com.chiccharmosa.dto.CustomerDto.CustomerResponse;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Component
public class CustomerAssembler implements RepresentationModelAssembler<CustomerResponse, EntityModel<CustomerResponse>> {

    @Override
    public EntityModel<CustomerResponse> toModel(CustomerResponse customer) {
        return EntityModel.of(customer,
                linkTo(methodOn(CustomerController.class).get(customer.id())).withSelfRel(),
                linkTo(methodOn(CustomerController.class).list(null, null, null)).withRel("customers"),
                linkTo(methodOn(CustomerController.class).listAddresses(customer.id(), null)).withRel("addresses"),
                Link.of(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/orders")
                        .queryParam("customerId", customer.id())
                        .toUriString(), "orders"));
    }

    public EntityModel<AddressResponse> toAddressModel(AddressResponse address) {
        return EntityModel.of(address,
                linkTo(methodOn(CustomerController.class).getAddress(address.customerId(), address.id()))
                        .withSelfRel(),
                linkTo(methodOn(CustomerController.class).get(address.customerId())).withRel("customer"));
    }
}
