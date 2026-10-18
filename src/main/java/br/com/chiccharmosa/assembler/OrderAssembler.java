package br.com.chiccharmosa.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import br.com.chiccharmosa.controller.CustomerController;
import br.com.chiccharmosa.controller.OrderController;
import br.com.chiccharmosa.dto.OrderDto.OrderResponse;
import br.com.chiccharmosa.entity.OrderStatus;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class OrderAssembler implements RepresentationModelAssembler<OrderResponse, EntityModel<OrderResponse>> {

    @Override
    public EntityModel<OrderResponse> toModel(OrderResponse order) {
        EntityModel<OrderResponse> model = EntityModel.of(order,
                linkTo(methodOn(OrderController.class).get(order.id())).withSelfRel(),
                linkTo(methodOn(OrderController.class).list(null, null, null, null, null)).withRel("orders"),
                linkTo(methodOn(CustomerController.class).get(order.customerId())).withRel("customer"));
        if (order.status() == OrderStatus.PENDING) {
            addAction(model, order, "pay", "order:pay");
            addAction(model, order, "cancel", "order:cancel");
            addAction(model, order, "shipping-cost", "order:create");
        }
        if (order.status() == OrderStatus.PAID) {
            addAction(model, order, "ship", "order:ship");
            addAction(model, order, "cancel", "order:cancel");
        }
        if (order.status() == OrderStatus.SHIPPED) {
            addAction(model, order, "deliver", "order:ship");
        }
        return model;
    }

    private void addAction(EntityModel<OrderResponse> model, OrderResponse order, String action, String permission) {
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
