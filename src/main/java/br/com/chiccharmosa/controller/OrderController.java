package br.com.chiccharmosa.controller;

import br.com.chiccharmosa.assembler.OrderAssembler;
import br.com.chiccharmosa.dto.OrderDto.OrderRequest;
import br.com.chiccharmosa.dto.OrderDto.OrderResponse;
import br.com.chiccharmosa.dto.OrderDto.PayRequest;
import br.com.chiccharmosa.dto.OrderDto.ShipRequest;
import br.com.chiccharmosa.dto.OrderDto.ShippingCostRequest;
import br.com.chiccharmosa.entity.OrderStatus;
import br.com.chiccharmosa.service.OrderService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderAssembler orderAssembler;
    private final PagedResourcesAssembler<OrderResponse> pagedOrderAssembler;

    public OrderController(OrderService orderService, OrderAssembler orderAssembler,
            PagedResourcesAssembler<OrderResponse> pagedOrderAssembler) {
        this.orderService = orderService;
        this.orderAssembler = orderAssembler;
        this.pagedOrderAssembler = pagedOrderAssembler;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('order:create')")
    public ResponseEntity<EntityModel<OrderResponse>> create(@Valid @RequestBody OrderRequest request) {
        boolean existed = request.id() != null && orderService.exists(request.id());
        EntityModel<OrderResponse> model = orderAssembler.toModel(orderService.create(request, Instant.now(), false));
        if (existed) {
            return ResponseEntity.ok(model);
        }
        return ResponseEntity.created(model.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(model);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('order:view')")
    public PagedModel<EntityModel<OrderResponse>> list(@RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID customerId, @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to, Pageable pageable) {
        return pagedOrderAssembler.toModel(orderService.list(status, customerId, from, to, pageable), orderAssembler);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('order:view')")
    public EntityModel<OrderResponse> get(@PathVariable UUID id) {
        return orderAssembler.toModel(orderService.get(id));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAuthority('order:pay')")
    public EntityModel<OrderResponse> pay(@PathVariable UUID id, @Valid @RequestBody PayRequest request) {
        return orderAssembler.toModel(orderService.pay(id, request.method(), Instant.now()));
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize("hasAuthority('order:ship')")
    public EntityModel<OrderResponse> ship(@PathVariable UUID id, @Valid @RequestBody ShipRequest request) {
        return orderAssembler.toModel(orderService.ship(id, request.method(), request.trackingCode(), Instant.now()));
    }

    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasAuthority('order:ship')")
    public EntityModel<OrderResponse> deliver(@PathVariable UUID id) {
        return orderAssembler.toModel(orderService.deliver(id, Instant.now()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('order:cancel')")
    public EntityModel<OrderResponse> cancel(@PathVariable UUID id) {
        return orderAssembler.toModel(orderService.cancel(id, Instant.now()));
    }

    @PatchMapping("/{id}/shipping-cost")
    @PreAuthorize("hasAuthority('order:create')")
    public EntityModel<OrderResponse> updateShippingCost(@PathVariable UUID id,
            @Valid @RequestBody ShippingCostRequest request) {
        return orderAssembler.toModel(orderService.updateShippingCost(id, request.shippingCost(), Instant.now()));
    }
}
