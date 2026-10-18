package br.com.chiccharmosa.controller;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import br.com.chiccharmosa.assembler.FinanceAssembler;
import br.com.chiccharmosa.dto.FinanceDto.AccountResponse;
import br.com.chiccharmosa.dto.FinanceDto.EntryRequest;
import br.com.chiccharmosa.dto.FinanceDto.EntryResponse;
import br.com.chiccharmosa.dto.FinanceDto.FinancialCategoryRequest;
import br.com.chiccharmosa.dto.FinanceDto.FinancialCategoryResponse;
import br.com.chiccharmosa.dto.FinanceDto.ProfitLine;
import br.com.chiccharmosa.dto.FinanceDto.ProfitResponse;
import br.com.chiccharmosa.entity.EntryType;
import br.com.chiccharmosa.service.FinancialService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.CollectionModel;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FinanceController {

    private final FinancialService financialService;
    private final FinanceAssembler financeAssembler;
    private final PagedResourcesAssembler<EntryResponse> pagedEntryAssembler;
    private final PagedResourcesAssembler<FinancialCategoryResponse> pagedCategoryAssembler;

    public FinanceController(FinancialService financialService, FinanceAssembler financeAssembler,
            PagedResourcesAssembler<EntryResponse> pagedEntryAssembler,
            PagedResourcesAssembler<FinancialCategoryResponse> pagedCategoryAssembler) {
        this.financialService = financialService;
        this.financeAssembler = financeAssembler;
        this.pagedEntryAssembler = pagedEntryAssembler;
        this.pagedCategoryAssembler = pagedCategoryAssembler;
    }

    @GetMapping("/api/financial-accounts")
    @PreAuthorize("hasAuthority('finance:view')")
    public CollectionModel<EntityModel<AccountResponse>> listAccounts() {
        List<EntityModel<AccountResponse>> accounts = new ArrayList<>();
        for (AccountResponse account : financialService.listAccounts()) {
            accounts.add(financeAssembler.toAccountModel(account));
        }
        return CollectionModel.of(accounts, linkTo(methodOn(FinanceController.class).listAccounts()).withSelfRel());
    }

    @GetMapping("/api/financial-categories")
    @PreAuthorize("hasAuthority('finance:view')")
    public PagedModel<EntityModel<FinancialCategoryResponse>> listCategories(Pageable pageable) {
        return pagedCategoryAssembler.toModel(financialService.listCategories(pageable),
                financeAssembler::toCategoryModel);
    }

    @PostMapping("/api/financial-categories")
    @PreAuthorize("hasAuthority('finance:edit')")
    public ResponseEntity<EntityModel<FinancialCategoryResponse>> createCategory(
            @Valid @RequestBody FinancialCategoryRequest request) {
        boolean existed = request.id() != null && financialService.categoryExists(request.id());
        EntityModel<FinancialCategoryResponse> model = financeAssembler.toCategoryModel(
                financialService.createCategory(request, Instant.now()));
        if (existed) {
            return ResponseEntity.ok(model);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(model);
    }

    @PostMapping("/api/financial-entries")
    @PreAuthorize("hasAuthority('finance:edit')")
    public ResponseEntity<EntityModel<EntryResponse>> createEntry(@Valid @RequestBody EntryRequest request) {
        boolean existed = request.id() != null && financialService.entryExists(request.id());
        EntityModel<EntryResponse> model = financeAssembler.toModel(financialService.create(request, Instant.now()));
        if (existed) {
            return ResponseEntity.ok(model);
        }
        return ResponseEntity.created(model.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(model);
    }

    @GetMapping("/api/financial-entries")
    @PreAuthorize("hasAuthority('finance:view')")
    public PagedModel<EntityModel<EntryResponse>> listEntries(@RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) EntryType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        return pagedEntryAssembler.toModel(financialService.list(accountId, type, from, to, pageable),
                financeAssembler);
    }

    @GetMapping("/api/financial-entries/{id}")
    @PreAuthorize("hasAuthority('finance:view')")
    public EntityModel<EntryResponse> getEntry(@PathVariable UUID id) {
        return financeAssembler.toModel(financialService.get(id));
    }

    @PutMapping("/api/financial-entries/{id}")
    @PreAuthorize("hasAuthority('finance:edit')")
    public EntityModel<EntryResponse> updateEntry(@PathVariable UUID id, @Valid @RequestBody EntryRequest request) {
        return financeAssembler.toModel(financialService.update(id, request, Instant.now()));
    }

    @DeleteMapping("/api/financial-entries/{id}")
    @PreAuthorize("hasAuthority('finance:edit')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEntry(@PathVariable UUID id) {
        financialService.delete(id, Instant.now());
    }

    @GetMapping("/api/finance/profit")
    @PreAuthorize("hasAuthority('finance:view')")
    public EntityModel<ProfitResponse> profit(@RequestParam UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return EntityModel.of(financialService.profit(accountId, from, to),
                linkTo(methodOn(FinanceController.class).profit(accountId, from, to)).withSelfRel());
    }

    @GetMapping("/api/finance/profit/breakdown")
    @PreAuthorize("hasAuthority('finance:view')")
    public CollectionModel<ProfitLine> profitBreakdown(@RequestParam UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String groupBy) {
        return CollectionModel.of(financialService.profitBreakdown(accountId, from, to, groupBy),
                linkTo(methodOn(FinanceController.class).profitBreakdown(accountId, from, to, groupBy))
                        .withSelfRel());
    }
}
