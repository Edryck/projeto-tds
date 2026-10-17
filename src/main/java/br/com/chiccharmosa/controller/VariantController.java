package br.com.chiccharmosa.controller;

import br.com.chiccharmosa.assembler.VariantAssembler;
import br.com.chiccharmosa.dto.VariantDto.VariantRequest;
import br.com.chiccharmosa.dto.VariantDto.VariantResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class VariantController {

    private final CatalogService catalogService;
    private final VariantAssembler variantAssembler;
    private final PagedResourcesAssembler<VariantResponse> pagedAssembler;

    public VariantController(CatalogService catalogService, VariantAssembler variantAssembler,
            PagedResourcesAssembler<VariantResponse> pagedAssembler) {
        this.catalogService = catalogService;
        this.variantAssembler = variantAssembler;
        this.pagedAssembler = pagedAssembler;
    }

    @PostMapping("/products/{productId}/variants")
    @PreAuthorize("hasAuthority('product:edit')")
    public ResponseEntity<EntityModel<VariantResponse>> create(@PathVariable UUID productId,
            @Valid @RequestBody VariantRequest request) {
        boolean existed = request.id() != null && catalogService.variantExists(request.id());
        EntityModel<VariantResponse> model = variantAssembler.toModel(
                catalogService.createVariant(productId, request, Instant.now()));
        if (existed) {
            return ResponseEntity.ok(model);
        }
        return ResponseEntity.created(model.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(model);
    }

    @GetMapping("/products/{productId}/variants")
    @PreAuthorize("hasAuthority('product:view')")
    public PagedModel<EntityModel<VariantResponse>> list(@PathVariable UUID productId, Pageable pageable) {
        return pagedAssembler.toModel(catalogService.listVariants(productId, pageable), variantAssembler);
    }

    @GetMapping("/variants/{id}")
    @PreAuthorize("hasAuthority('product:view')")
    public EntityModel<VariantResponse> get(@PathVariable UUID id) {
        return variantAssembler.toModel(catalogService.findVariant(id));
    }

    @PutMapping("/variants/{id}")
    @PreAuthorize("hasAuthority('product:edit')")
    public EntityModel<VariantResponse> update(@PathVariable UUID id, @Valid @RequestBody VariantRequest request) {
        return variantAssembler.toModel(catalogService.updateVariant(id, request, Instant.now()));
    }

    @DeleteMapping("/variants/{id}")
    @PreAuthorize("hasAuthority('product:edit')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        catalogService.deleteVariant(id, Instant.now());
    }
}
