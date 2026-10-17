package br.com.chiccharmosa.controller;

import br.com.chiccharmosa.assembler.ProductAssembler;
import br.com.chiccharmosa.dto.ProductDto.ProductRequest;
import br.com.chiccharmosa.dto.ProductDto.ProductResponse;
import br.com.chiccharmosa.service.CatalogService;
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
@RequestMapping("/api/products")
public class ProductController {

    private final CatalogService catalogService;
    private final ProductAssembler productAssembler;
    private final PagedResourcesAssembler<ProductResponse> pagedAssembler;

    public ProductController(CatalogService catalogService, ProductAssembler productAssembler,
            PagedResourcesAssembler<ProductResponse> pagedAssembler) {
        this.catalogService = catalogService;
        this.productAssembler = productAssembler;
        this.pagedAssembler = pagedAssembler;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('product:edit')")
    public ResponseEntity<EntityModel<ProductResponse>> create(@Valid @RequestBody ProductRequest request) {
        boolean existed = request.id() != null && catalogService.productExists(request.id());
        EntityModel<ProductResponse> model = productAssembler.toModel(
                catalogService.createProduct(request, Instant.now()));
        if (existed) {
            return ResponseEntity.ok(model);
        }
        return ResponseEntity.created(model.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(model);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('product:view')")
    public PagedModel<EntityModel<ProductResponse>> list(@RequestParam(required = false) String name,
            @RequestParam(required = false) UUID categoryId, Pageable pageable) {
        return pagedAssembler.toModel(catalogService.listProducts(name, categoryId, pageable), productAssembler);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('product:view')")
    public EntityModel<ProductResponse> get(@PathVariable UUID id) {
        return productAssembler.toModel(catalogService.getProduct(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('product:edit')")
    public EntityModel<ProductResponse> update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return productAssembler.toModel(catalogService.updateProduct(id, request, Instant.now()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('product:edit')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        catalogService.deleteProduct(id, Instant.now());
    }
}
