package br.com.chiccharmosa.service;

import br.com.chiccharmosa.dto.CustomerDto.AddressRequest;
import br.com.chiccharmosa.dto.CustomerDto.AddressResponse;
import br.com.chiccharmosa.dto.CustomerDto.CustomerRequest;
import br.com.chiccharmosa.dto.CustomerDto.CustomerResponse;
import br.com.chiccharmosa.entity.Address;
import br.com.chiccharmosa.entity.Customer;
import br.com.chiccharmosa.exception.BusinessException;
import br.com.chiccharmosa.repository.AddressRepository;
import br.com.chiccharmosa.repository.CustomerRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;
    private final AuditService auditService;

    public CustomerService(CustomerRepository customerRepository, AddressRepository addressRepository,
            AuditService auditService) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.auditService = auditService;
    }

    public boolean exists(UUID id) {
        return customerRepository.existsById(id);
    }

    public boolean addressExists(UUID customerId, UUID addressId) {
        return addressRepository.existsByIdAndCustomerId(addressId, customerId);
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request, Instant changedAt) {
        if (request.id() != null) {
            Customer existing = customerRepository.findById(request.id()).orElse(null);
            if (existing != null) {
                return CustomerResponse.from(existing);
            }
        }
        Customer customer = new Customer();
        customer.setId(request.id() == null ? UUID.randomUUID() : request.id());
        applyCustomerRequest(customer, request);
        customer.touch(changedAt);
        CustomerResponse response = CustomerResponse.from(customerRepository.save(customer));
        auditService.record(AuditService.CUSTOMER, customer.getId(), AuditService.CREATE, null, response, changedAt);
        return response;
    }

    public CustomerResponse get(UUID id) {
        return CustomerResponse.from(findActiveCustomer(id));
    }

    public Page<CustomerResponse> list(String name, String phone, Pageable pageable) {
        if (name != null && !name.isBlank()) {
            return customerRepository.findByDeletedAtIsNullAndNameContainingIgnoreCase(name, pageable)
                    .map(CustomerResponse::from);
        }
        if (phone != null && !phone.isBlank()) {
            return customerRepository.findByDeletedAtIsNullAndPhoneContaining(phone, pageable)
                    .map(CustomerResponse::from);
        }
        return customerRepository.findByDeletedAtIsNull(pageable).map(CustomerResponse::from);
    }

    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request, Instant changedAt) {
        Customer customer = findActiveCustomer(id);
        CustomerResponse before = CustomerResponse.from(customer);
        applyCustomerRequest(customer, request);
        customer.touch(changedAt);
        CustomerResponse after = CustomerResponse.from(customer);
        auditService.record(AuditService.CUSTOMER, id, AuditService.UPDATE, before, after, changedAt);
        return after;
    }

    @Transactional
    public void delete(UUID id, Instant changedAt) {
        Customer customer = findActiveCustomer(id);
        CustomerResponse before = CustomerResponse.from(customer);
        customer.markDeleted(changedAt);
        auditService.record(AuditService.CUSTOMER, id, AuditService.DELETE, before, null, changedAt);
    }

    public Page<AddressResponse> listAddresses(UUID customerId, Pageable pageable) {
        findActiveCustomer(customerId);
        return addressRepository.findByCustomerIdAndDeletedAtIsNull(customerId, pageable)
                .map(AddressResponse::from);
    }

    public AddressResponse getAddress(UUID customerId, UUID addressId) {
        findActiveCustomer(customerId);
        return AddressResponse.from(findActiveAddress(customerId, addressId));
    }

    @Transactional
    public AddressResponse addAddress(UUID customerId, AddressRequest request, Instant changedAt) {
        findActiveCustomer(customerId);
        if (request.id() != null) {
            Address existing = addressRepository.findByIdAndCustomerId(request.id(), customerId).orElse(null);
            if (existing != null) {
                return AddressResponse.from(existing);
            }
        }
        Address address = new Address();
        address.setId(request.id() == null ? UUID.randomUUID() : request.id());
        address.setCustomerId(customerId);
        applyAddressRequest(address, request);
        address.touch(changedAt);
        return AddressResponse.from(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse updateAddress(UUID customerId, UUID addressId, AddressRequest request,
            Instant changedAt) {
        findActiveCustomer(customerId);
        Address address = findActiveAddress(customerId, addressId);
        applyAddressRequest(address, request);
        address.touch(changedAt);
        return AddressResponse.from(address);
    }

    @Transactional
    public void deleteAddress(UUID customerId, UUID addressId, Instant changedAt) {
        findActiveCustomer(customerId);
        findActiveAddress(customerId, addressId).markDeleted(changedAt);
    }

    private Customer findActiveCustomer(UUID id) {
        return customerRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Cliente não encontrado"));
    }

    private Address findActiveAddress(UUID customerId, UUID addressId) {
        return addressRepository.findByIdAndCustomerIdAndDeletedAtIsNull(addressId, customerId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Endereço não encontrado"));
    }

    private void applyCustomerRequest(Customer customer, CustomerRequest request) {
        customer.setName(request.name());
        customer.setPhone(request.phone());
        customer.setEmail(request.email());
        customer.setNote(request.note());
    }

    private void applyAddressRequest(Address address, AddressRequest request) {
        address.setStreet(request.street());
        address.setNumber(request.number());
        address.setComplement(request.complement());
        address.setDistrict(request.district());
        address.setCity(request.city());
        address.setState(request.state());
        address.setZipCode(request.zipCode());
    }
}
