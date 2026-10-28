package br.com.chiccharmosa.controller;

import br.com.chiccharmosa.assembler.OrderAssembler;
import br.com.chiccharmosa.dto.OrderDto.OrderRequest;
import br.com.chiccharmosa.dto.OrderDto.OrderResponse;
import br.com.chiccharmosa.dto.OrderDto.OrderView;
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
    private final PagedResourcesAssembler<OrderView> pagedOrderAssembler;

    public OrderController(OrderService orderService, OrderAssembler orderAssembler,
            PagedResourcesAssembler<OrderView> pagedOrderAssembler) {
        this.orderService = orderService;
        this.orderAssembler = orderAssembler;
        this.pagedOrderAssembler = pagedOrderAssembler;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('order:create')")
    public ResponseEntity<EntityModel<OrderView>> create(@Valid @RequestBody OrderRequest request) {
        boolean existed = request.id() != null && orderService.exists(request.id());
        OrderResponse created = orderService.create(request, Instant.now(), false);
        EntityModel<OrderView> model = orderAssembler.toModel(orderService.getView(created.id()));
        if (existed) {
            return ResponseEntity.ok(model);
        }
        return ResponseEntity.created(model.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(model);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('order:view')")
    public PagedModel<EntityModel<OrderView>> list(@RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID customerId, @RequestParam(required = false) String q,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            Pageable pageable) {
        return pagedOrderAssembler.toModel(orderService.listViews(status, customerId, q, from, to, pageable),
                orderAssembler);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('order:view')")
    public EntityModel<OrderView> get(@PathVariable UUID id) {
        return orderAssembler.toModel(orderService.getView(id));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAuthority('order:pay')")
    public EntityModel<OrderView> pay(@PathVariable UUID id, @Valid @RequestBody PayRequest request) {
        orderService.pay(id, request.method(), Instant.now());
        return orderAssembler.toModel(orderService.getView(id));
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize("hasAuthority('order:ship')")
    public EntityModel<OrderView> ship(@PathVariable UUID id, @Valid @RequestBody ShipRequest request) {
        orderService.ship(id, request.method(), request.trackingCode(), Instant.now());
        return orderAssembler.toModel(orderService.getView(id));
    }

    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasAuthority('order:ship')")
    public EntityModel<OrderView> deliver(@PathVariable UUID id) {
        orderService.deliver(id, Instant.now());
        return orderAssembler.toModel(orderService.getView(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('order:cancel')")
    public EntityModel<OrderView> cancel(@PathVariable UUID id) {
        orderService.cancel(id, Instant.now());
        return orderAssembler.toModel(orderService.getView(id));
    }

    @PatchMapping("/{id}/shipping-cost")
    @PreAuthorize("hasAuthority('order:create')")
    public EntityModel<OrderView> updateShippingCost(@PathVariable UUID id,
            @Valid @RequestBody ShippingCostRequest request) {
        orderService.updateShippingCost(id, request.shippingCost(), Instant.now());
        return orderAssembler.toModel(orderService.getView(id));
    }
}
