package br.com.chiccharmosa.controller;

import br.com.chiccharmosa.assembler.SupplierAssembler;
import br.com.chiccharmosa.dto.SupplierDto.SupplierRequest;
import br.com.chiccharmosa.dto.SupplierDto.SupplierResponse;
import br.com.chiccharmosa.service.StockService;
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
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final StockService stockService;
    private final SupplierAssembler supplierAssembler;
    private final PagedResourcesAssembler<SupplierResponse> pagedAssembler;

    public SupplierController(StockService stockService, SupplierAssembler supplierAssembler,
            PagedResourcesAssembler<SupplierResponse> pagedAssembler) {
        this.stockService = stockService;
        this.supplierAssembler = supplierAssembler;
        this.pagedAssembler = pagedAssembler;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('stock:adjust')")
    public ResponseEntity<EntityModel<SupplierResponse>> create(@Valid @RequestBody SupplierRequest request) {
        boolean existed = request.id() != null && stockService.supplierExists(request.id());
        EntityModel<SupplierResponse> model = supplierAssembler.toModel(
                stockService.createSupplier(request, Instant.now()));
        if (existed) {
            return ResponseEntity.ok(model);
        }
        return ResponseEntity.created(model.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(model);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('stock:view')")
    public PagedModel<EntityModel<SupplierResponse>> list(@RequestParam(required = false) String name,
            Pageable pageable) {
        return pagedAssembler.toModel(stockService.listSuppliers(name, pageable), supplierAssembler);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('stock:view')")
    public EntityModel<SupplierResponse> get(@PathVariable UUID id) {
        return supplierAssembler.toModel(stockService.getSupplier(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('stock:adjust')")
    public EntityModel<SupplierResponse> update(@PathVariable UUID id, @Valid @RequestBody SupplierRequest request) {
        return supplierAssembler.toModel(stockService.updateSupplier(id, request, Instant.now()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('stock:adjust')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        stockService.deleteSupplier(id, Instant.now());
    }
}
