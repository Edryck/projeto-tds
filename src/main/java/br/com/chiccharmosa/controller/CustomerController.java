package br.com.chiccharmosa.controller;

import br.com.chiccharmosa.assembler.CustomerAssembler;
import br.com.chiccharmosa.dto.CustomerDto.AddressRequest;
import br.com.chiccharmosa.dto.CustomerDto.AddressResponse;
import br.com.chiccharmosa.dto.CustomerDto.CustomerRequest;
import br.com.chiccharmosa.dto.CustomerDto.CustomerResponse;
import br.com.chiccharmosa.service.CustomerService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerAssembler customerAssembler;
    private final PagedResourcesAssembler<CustomerResponse> pagedCustomerAssembler;
    private final PagedResourcesAssembler<AddressResponse> pagedAddressAssembler;

    public CustomerController(CustomerService customerService, CustomerAssembler customerAssembler,
            PagedResourcesAssembler<CustomerResponse> pagedCustomerAssembler,
            PagedResourcesAssembler<AddressResponse> pagedAddressAssembler) {
        this.customerService = customerService;
        this.customerAssembler = customerAssembler;
        this.pagedCustomerAssembler = pagedCustomerAssembler;
        this.pagedAddressAssembler = pagedAddressAssembler;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('customer:edit')")
    public ResponseEntity<EntityModel<CustomerResponse>> create(@Valid @RequestBody CustomerRequest request) {
        boolean existed = request.id() != null && customerService.exists(request.id());
        EntityModel<CustomerResponse> model = customerAssembler.toModel(
                customerService.create(request, Instant.now()));
        if (existed) {
            return ResponseEntity.ok(model);
        }
        return ResponseEntity.created(model.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(model);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('customer:view')")
    public PagedModel<EntityModel<CustomerResponse>> list(@RequestParam(required = false) String name,
            @RequestParam(required = false) String phone, Pageable pageable) {
        return pagedCustomerAssembler.toModel(customerService.list(name, phone, pageable), customerAssembler);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('customer:view')")
    public EntityModel<CustomerResponse> get(@PathVariable UUID id) {
        return customerAssembler.toModel(customerService.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('customer:edit')")
    public EntityModel<CustomerResponse> update(@PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
        return customerAssembler.toModel(customerService.update(id, request, Instant.now()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('customer:edit')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        customerService.delete(id, Instant.now());
    }

    @GetMapping("/{id}/addresses")
    @PreAuthorize("hasAuthority('customer:view')")
    public PagedModel<EntityModel<AddressResponse>> listAddresses(@PathVariable UUID id, Pageable pageable) {
        return pagedAddressAssembler.toModel(customerService.listAddresses(id, pageable),
                customerAssembler::toAddressModel);
    }

    @GetMapping("/{id}/addresses/{addressId}")
    @PreAuthorize("hasAuthority('customer:view')")
    public EntityModel<AddressResponse> getAddress(@PathVariable UUID id, @PathVariable UUID addressId) {
        return customerAssembler.toAddressModel(customerService.getAddress(id, addressId));
    }

    @PostMapping("/{id}/addresses")
    @PreAuthorize("hasAuthority('customer:edit')")
    public ResponseEntity<EntityModel<AddressResponse>> addAddress(@PathVariable UUID id,
            @Valid @RequestBody AddressRequest request) {
        boolean existed = request.id() != null && customerService.addressExists(id, request.id());
        EntityModel<AddressResponse> model = customerAssembler.toAddressModel(
                customerService.addAddress(id, request, Instant.now()));
        if (existed) {
            return ResponseEntity.ok(model);
        }
        return ResponseEntity.created(model.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(model);
    }

    @PutMapping("/{id}/addresses/{addressId}")
    @PreAuthorize("hasAuthority('customer:edit')")
    public EntityModel<AddressResponse> updateAddress(@PathVariable UUID id, @PathVariable UUID addressId,
            @Valid @RequestBody AddressRequest request) {
        return customerAssembler.toAddressModel(
                customerService.updateAddress(id, addressId, request, Instant.now()));
    }

    @DeleteMapping("/{id}/addresses/{addressId}")
    @PreAuthorize("hasAuthority('customer:edit')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(@PathVariable UUID id, @PathVariable UUID addressId) {
        customerService.deleteAddress(id, addressId, Instant.now());
    }
}
