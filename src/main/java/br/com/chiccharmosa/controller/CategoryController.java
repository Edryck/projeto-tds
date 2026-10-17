package br.com.chiccharmosa.controller;

import br.com.chiccharmosa.assembler.CategoryAssembler;
import br.com.chiccharmosa.dto.CategoryDto.CategoryRequest;
import br.com.chiccharmosa.dto.CategoryDto.CategoryResponse;
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
@RequestMapping("/api/categories")
public class CategoryController {

    private final CatalogService catalogService;
    private final CategoryAssembler categoryAssembler;
    private final PagedResourcesAssembler<CategoryResponse> pagedAssembler;

    public CategoryController(CatalogService catalogService, CategoryAssembler categoryAssembler,
            PagedResourcesAssembler<CategoryResponse> pagedAssembler) {
        this.catalogService = catalogService;
        this.categoryAssembler = categoryAssembler;
        this.pagedAssembler = pagedAssembler;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('product:edit')")
    public ResponseEntity<EntityModel<CategoryResponse>> create(@Valid @RequestBody CategoryRequest request) {
        boolean existed = request.id() != null && catalogService.categoryExists(request.id());
        EntityModel<CategoryResponse> model = categoryAssembler.toModel(
                catalogService.createCategory(request, Instant.now()));
        if (existed) {
            return ResponseEntity.ok(model);
        }
        return ResponseEntity.created(model.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(model);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('product:view')")
    public PagedModel<EntityModel<CategoryResponse>> list(@RequestParam(required = false) String name,
            Pageable pageable) {
        return pagedAssembler.toModel(catalogService.listCategories(name, pageable), categoryAssembler);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('product:view')")
    public EntityModel<CategoryResponse> get(@PathVariable UUID id) {
        return categoryAssembler.toModel(catalogService.getCategory(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('product:edit')")
    public EntityModel<CategoryResponse> update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return categoryAssembler.toModel(catalogService.updateCategory(id, request, Instant.now()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('product:edit')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        catalogService.deleteCategory(id, Instant.now());
    }
}
