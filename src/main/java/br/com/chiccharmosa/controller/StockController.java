package br.com.chiccharmosa.controller;

import br.com.chiccharmosa.assembler.StockAssembler;
import br.com.chiccharmosa.dto.StockDto.AdjustmentRequest;
import br.com.chiccharmosa.dto.StockDto.MovementResponse;
import br.com.chiccharmosa.dto.StockDto.StockEntryRequest;
import br.com.chiccharmosa.dto.StockDto.StockEntryResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StockController {

    private final StockService stockService;
    private final StockAssembler stockAssembler;
    private final PagedResourcesAssembler<StockEntryResponse> pagedEntryAssembler;
    private final PagedResourcesAssembler<MovementResponse> pagedMovementAssembler;

    public StockController(StockService stockService, StockAssembler stockAssembler,
            PagedResourcesAssembler<StockEntryResponse> pagedEntryAssembler,
            PagedResourcesAssembler<MovementResponse> pagedMovementAssembler) {
        this.stockService = stockService;
        this.stockAssembler = stockAssembler;
        this.pagedEntryAssembler = pagedEntryAssembler;
        this.pagedMovementAssembler = pagedMovementAssembler;
    }

    @PostMapping("/stock-entries")
    @PreAuthorize("hasAuthority('stock:adjust')")
    public ResponseEntity<EntityModel<StockEntryResponse>> createEntry(@Valid @RequestBody StockEntryRequest request) {
        boolean existed = request.id() != null && stockService.entryExists(request.id());
        EntityModel<StockEntryResponse> model = stockAssembler.toModel(
                stockService.registerEntry(request, Instant.now()));
        if (existed) {
            return ResponseEntity.ok(model);
        }
        return ResponseEntity.created(model.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(model);
    }

    @GetMapping("/stock-entries")
    @PreAuthorize("hasAuthority('stock:view')")
    public PagedModel<EntityModel<StockEntryResponse>> listEntries(Pageable pageable) {
        return pagedEntryAssembler.toModel(stockService.listEntries(pageable), stockAssembler);
    }

    @GetMapping("/stock-entries/{id}")
    @PreAuthorize("hasAuthority('stock:view')")
    public EntityModel<StockEntryResponse> getEntry(@PathVariable UUID id) {
        return stockAssembler.toModel(stockService.findEntry(id));
    }

    @GetMapping("/variants/{id}/movements")
    @PreAuthorize("hasAuthority('stock:view')")
    public PagedModel<EntityModel<MovementResponse>> listMovements(@PathVariable UUID id, Pageable pageable) {
        return pagedMovementAssembler.toModel(stockService.listMovements(id, pageable),
                stockAssembler::toMovementModel);
    }

    @PostMapping("/variants/{id}/adjustments")
    @PreAuthorize("hasAuthority('stock:adjust')")
    @ResponseStatus(HttpStatus.CREATED)
    public EntityModel<MovementResponse> adjust(@PathVariable UUID id, @Valid @RequestBody AdjustmentRequest request) {
        return stockAssembler.toMovementModel(
                stockService.adjust(id, request.quantity(), request.reason(), Instant.now()));
    }
}
