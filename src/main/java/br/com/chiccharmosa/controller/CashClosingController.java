package br.com.chiccharmosa.controller;

import br.com.chiccharmosa.assembler.CashClosingAssembler;
import br.com.chiccharmosa.dto.FinanceDto.CashClosingRequest;
import br.com.chiccharmosa.dto.FinanceDto.CashClosingResponse;
import br.com.chiccharmosa.service.FinancialService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CashClosingController {

    private final FinancialService financialService;
    private final CashClosingAssembler closingAssembler;
    private final PagedResourcesAssembler<CashClosingResponse> pagedAssembler;

    public CashClosingController(FinancialService financialService, CashClosingAssembler closingAssembler,
            PagedResourcesAssembler<CashClosingResponse> pagedAssembler) {
        this.financialService = financialService;
        this.closingAssembler = closingAssembler;
        this.pagedAssembler = pagedAssembler;
    }

    @PostMapping("/api/cash-closings")
    @PreAuthorize("hasAuthority('finance:edit')")
    public ResponseEntity<EntityModel<CashClosingResponse>> close(@Valid @RequestBody CashClosingRequest request) {
        boolean existed = request.id() != null && financialService.closingExists(request.id());
        EntityModel<CashClosingResponse> model = closingAssembler.toModel(financialService.close(request));
        if (existed) {
            return ResponseEntity.ok(model);
        }
        return ResponseEntity.created(model.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(model);
    }

    @GetMapping("/api/cash-closings")
    @PreAuthorize("hasAuthority('finance:view')")
    public PagedModel<EntityModel<CashClosingResponse>> list(@RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        return pagedAssembler.toModel(financialService.listClosings(accountId, from, to, pageable), closingAssembler);
    }

    @GetMapping("/api/cash-closings/{id}")
    @PreAuthorize("hasAuthority('finance:view')")
    public EntityModel<CashClosingResponse> get(@PathVariable UUID id) {
        return closingAssembler.toModel(financialService.getClosing(id));
    }
}
