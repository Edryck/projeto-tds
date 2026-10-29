package br.com.chiccharmosa.controller;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import br.com.chiccharmosa.dto.CancellationDto.CancellationView;
import br.com.chiccharmosa.dto.CancellationDto.DecisionBody;
import br.com.chiccharmosa.entity.CancellationStatus;
import br.com.chiccharmosa.service.OrderService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cancellation-requests")
public class CancellationController {

    private final OrderService orderService;
    private final PagedResourcesAssembler<CancellationView> pagedAssembler;

    public CancellationController(OrderService orderService, PagedResourcesAssembler<CancellationView> pagedAssembler) {
        this.orderService = orderService;
        this.pagedAssembler = pagedAssembler;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('order:cancel')")
    public PagedModel<EntityModel<CancellationView>> list(@RequestParam(required = false) CancellationStatus status,
            Pageable pageable) {
        return pagedAssembler.toModel(orderService.listCancellations(status, pageable), this::toModel);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('order:cancel')")
    public EntityModel<CancellationView> approve(@PathVariable UUID id,
            @Valid @RequestBody(required = false) DecisionBody body) {
        return toModel(orderService.approveCancellation(id, responseOf(body), Instant.now()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('order:cancel')")
    public EntityModel<CancellationView> reject(@PathVariable UUID id,
            @Valid @RequestBody(required = false) DecisionBody body) {
        return toModel(orderService.rejectCancellation(id, responseOf(body), Instant.now()));
    }

    private String responseOf(DecisionBody body) {
        return body == null ? null : body.response();
    }

    private EntityModel<CancellationView> toModel(CancellationView view) {
        EntityModel<CancellationView> model = EntityModel.of(view,
                linkTo(methodOn(CancellationController.class).list(null, null)).withRel("cancellationRequests"),
                linkTo(methodOn(OrderController.class).get(view.orderId())).withRel("order"));
        if (view.status() == CancellationStatus.PENDING) {
            model.add(linkTo(CancellationController.class).slash(view.id()).slash("approve").withRel("approve"));
            model.add(linkTo(CancellationController.class).slash(view.id()).slash("reject").withRel("reject"));
        }
        return model;
    }
}
