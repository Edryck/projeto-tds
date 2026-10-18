package br.com.chiccharmosa.controller;

import br.com.chiccharmosa.assembler.AuditAssembler;
import br.com.chiccharmosa.dto.AuditDto.AuditResponse;
import br.com.chiccharmosa.service.AuditService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditController {

    private final AuditService auditService;
    private final AuditAssembler auditAssembler;
    private final PagedResourcesAssembler<AuditResponse> pagedAuditAssembler;

    public AuditController(AuditService auditService, AuditAssembler auditAssembler,
            PagedResourcesAssembler<AuditResponse> pagedAuditAssembler) {
        this.auditService = auditService;
        this.auditAssembler = auditAssembler;
        this.pagedAuditAssembler = pagedAuditAssembler;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:view')")
    public PagedModel<EntityModel<AuditResponse>> list(@RequestParam(required = false) String entity,
            @RequestParam(required = false) UUID entityId, @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String action, @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(sort = "actedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return pagedAuditAssembler.toModel(auditService.list(entity, entityId, userId, action, from, to, pageable),
                auditAssembler);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('audit:view')")
    public EntityModel<AuditResponse> get(@PathVariable UUID id) {
        return auditAssembler.toModel(auditService.get(id));
    }
}
