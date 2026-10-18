package br.com.chiccharmosa.dto;

import br.com.chiccharmosa.entity.Order;
import br.com.chiccharmosa.entity.OrderItem;
import br.com.chiccharmosa.entity.OrderStatus;
import br.com.chiccharmosa.entity.PaymentMethod;
import br.com.chiccharmosa.entity.ShippingMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.hateoas.server.core.Relation;

public final class OrderDto {

    private OrderDto() {
    }

    public record OrderRequest(UUID id, @NotNull UUID customerId, @Size(max = 1000) String note,
            @PositiveOrZero BigDecimal shippingCost, String couponCode, @NotEmpty List<@Valid ItemRequest> items) {
    }

    public record ItemRequest(UUID id, @NotNull UUID variantId, @Positive int quantity) {
    }

    public record PayRequest(@NotNull PaymentMethod method) {
    }

    public record ShipRequest(@NotNull ShippingMethod method, String trackingCode) {
    }

    public record ShippingCostRequest(@NotNull @PositiveOrZero BigDecimal shippingCost) {
    }

    public record ItemResponse(UUID id, UUID variantId, int quantity, BigDecimal unitPrice, BigDecimal unitCost) {

        public static ItemResponse from(OrderItem item) {
            return new ItemResponse(item.getId(), item.getVariant().getId(), item.getQuantity(), item.getUnitPrice(),
                    item.getUnitCost());
        }
    }

    @Relation(collectionRelation = "orders")
    public record OrderResponse(UUID id, UUID customerId, String customerName, OrderStatus status,
            List<ItemResponse> items, BigDecimal subtotal, BigDecimal discount, BigDecimal shippingCost,
            BigDecimal total, String note, Instant orderedAt) {

        public static OrderResponse from(Order order) {
            List<ItemResponse> items = new ArrayList<>();
            for (OrderItem item : order.getItems()) {
                items.add(ItemResponse.from(item));
            }
            return new OrderResponse(order.getId(), order.getCustomer().getId(), order.getCustomer().getName(),
                    order.getStatus(), items, order.getSubtotal(), order.getDiscount(), order.getShippingCost(),
                    order.getTotal(), order.getNote(), order.getOrderedAt());
        }
    }
}
