package br.com.chiccharmosa.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import br.com.chiccharmosa.controller.CustomerController;
import br.com.chiccharmosa.controller.OrderController;
import br.com.chiccharmosa.dto.OrderDto.OrderView;
import br.com.chiccharmosa.entity.OrderStatus;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class OrderAssembler implements RepresentationModelAssembler<OrderView, EntityModel<OrderView>> {

    @Override
    public EntityModel<OrderView> toModel(OrderView order) {
        EntityModel<OrderView> model = EntityModel.of(order,
                linkTo(methodOn(OrderController.class).get(order.id())).withSelfRel(),
                linkTo(methodOn(OrderController.class).list(null, null, null, null, null, null)).withRel("orders"),
                linkTo(methodOn(CustomerController.class).get(order.customerId())).withRel("customer"));
        boolean pending = order.pendingCancellation() != null;
        if (order.status() == OrderStatus.PENDING) {
            if (!pending) {
                addAction(model, order, "pay", "order:pay");
            }
            addAction(model, order, "cancel", "order:cancel");
            addAction(model, order, "shipping-cost", "order:create");
        }
        if (order.status() == OrderStatus.PAID) {
            if (!pending) {
                addAction(model, order, "ship", "order:ship");
            }
            addAction(model, order, "cancel", "order:cancel");
        }
        if (order.status() == OrderStatus.SHIPPED && !pending) {
            addAction(model, order, "deliver", "order:ship");
        }
        boolean cancellable = order.status() == OrderStatus.PENDING || order.status() == OrderStatus.PAID;
        if (cancellable && !pending && hasPermission("order:request-cancel")) {
            model.add(linkTo(OrderController.class).slash(order.id()).slash("cancellation-requests")
                    .withRel("request-cancel"));
        }
        return model;
    }

    private void addAction(EntityModel<OrderView> model, OrderView order, String action, String permission) {
        if (hasPermission(permission)) {
            model.add(linkTo(OrderController.class).slash(order.id()).slash(action).withRel(action));
        }
    }

    private boolean hasPermission(String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(permission));
    }
}
