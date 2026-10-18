package br.com.chiccharmosa.service;

import br.com.chiccharmosa.dto.OrderDto.ItemRequest;
import br.com.chiccharmosa.dto.OrderDto.OrderRequest;
import br.com.chiccharmosa.dto.OrderDto.OrderResponse;
import br.com.chiccharmosa.entity.Coupon;
import br.com.chiccharmosa.entity.Customer;
import br.com.chiccharmosa.entity.Order;
import br.com.chiccharmosa.entity.OrderItem;
import br.com.chiccharmosa.entity.OrderStatus;
import br.com.chiccharmosa.entity.Payment;
import br.com.chiccharmosa.entity.PaymentMethod;
import br.com.chiccharmosa.entity.ProductVariant;
import br.com.chiccharmosa.entity.Shipment;
import br.com.chiccharmosa.entity.ShippingMethod;
import br.com.chiccharmosa.exception.BusinessException;
import br.com.chiccharmosa.repository.CustomerRepository;
import br.com.chiccharmosa.repository.OrderRepository;
import br.com.chiccharmosa.repository.PaymentRepository;
import br.com.chiccharmosa.repository.ProductVariantRepository;
import br.com.chiccharmosa.repository.ShipmentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final HttpStatus UNPROCESSABLE = HttpStatus.valueOf(422);
    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T23:59:59Z");

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductVariantRepository variantRepository;
    private final PaymentRepository paymentRepository;
    private final ShipmentRepository shipmentRepository;
    private final StockService stockService;
    private final CouponService couponService;

    public OrderService(OrderRepository orderRepository, CustomerRepository customerRepository,
            ProductVariantRepository variantRepository, PaymentRepository paymentRepository,
            ShipmentRepository shipmentRepository, StockService stockService, CouponService couponService) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.variantRepository = variantRepository;
        this.paymentRepository = paymentRepository;
        this.shipmentRepository = shipmentRepository;
        this.stockService = stockService;
        this.couponService = couponService;
    }

    public boolean exists(UUID id) {
        return orderRepository.existsById(id);
    }

    @Transactional
    public OrderResponse create(OrderRequest request, Instant changedAt, boolean allowNegativeStock) {
        if (request.id() != null) {
            Order existing = orderRepository.findById(request.id()).orElse(null);
            if (existing != null) {
                return OrderResponse.from(existing);
            }
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Pedido precisa de pelo menos um item");
        }
        if (request.shippingCost() != null && request.shippingCost().signum() < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Frete não pode ser negativo");
        }
        Customer customer = customerRepository.findByIdAndDeletedAtIsNull(request.customerId())
                .orElseThrow(() -> new BusinessException(UNPROCESSABLE, "Cliente inexistente"));
        Coupon coupon = null;
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            coupon = couponService.redeem(request.couponCode(), changedAt);
        }

        Order order = new Order();
        order.setId(request.id() == null ? UUID.randomUUID() : request.id());
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);
        order.setNote(request.note());
        order.setCouponId(coupon == null ? null : coupon.getId());
        order.setOrderedAt(changedAt);
        order.touch(changedAt);

        BigDecimal subtotal = BigDecimal.ZERO;
        Map<UUID, Integer> quantityByVariant = new TreeMap<>();
        for (ItemRequest requestItem : request.items()) {
            if (requestItem.quantity() <= 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Quantidade deve ser maior que zero");
            }
            ProductVariant variant = variantRepository.findByIdAndDeletedAtIsNull(requestItem.variantId())
                    .orElseThrow(() -> new BusinessException(UNPROCESSABLE, "Variante inexistente"));
            OrderItem item = new OrderItem();
            item.setId(requestItem.id() == null ? UUID.randomUUID() : requestItem.id());
            item.setVariant(variant);
            item.setQuantity(requestItem.quantity());
            item.setUnitPrice(variant.getPrice());
            item.setUnitCost(variant.getCost());
            item.touch(changedAt);
            order.addItem(item);
            subtotal = subtotal.add(variant.getPrice().multiply(BigDecimal.valueOf(requestItem.quantity())));
            quantityByVariant.merge(variant.getId(), requestItem.quantity(), Integer::sum);
        }

        // sempre na mesma ordem de variante, senão dois pedidos simultâneos se travam um esperando o outro
        for (Map.Entry<UUID, Integer> entry : quantityByVariant.entrySet()) {
            stockService.withdraw(entry.getKey(), entry.getValue(), order.getId(), allowNegativeStock);
        }

        BigDecimal shippingCost = request.shippingCost() == null ? BigDecimal.ZERO : request.shippingCost();
        BigDecimal discount = BigDecimal.ZERO;
        if (coupon != null) {
            discount = money(subtotal.multiply(coupon.getDiscountPercent()).divide(BigDecimal.valueOf(100)));
        }
        order.setSubtotal(money(subtotal));
        order.setDiscount(money(discount));
        order.setShippingCost(money(shippingCost));
        order.setTotal(money(subtotal.subtract(discount).add(shippingCost)));
        return OrderResponse.from(orderRepository.save(order));
    }

    public OrderResponse get(UUID id) {
        return OrderResponse.from(findActiveOrder(id));
    }

    public Page<OrderResponse> list(OrderStatus status, UUID customerId, Instant from, Instant to,
            Pageable pageable) {
        // postgres não descobre o tipo de um Instant nulo na query, então troco por limites bem largos
        Instant start = from == null ? Instant.EPOCH : from;
        Instant end = to == null ? FAR_FUTURE : to;
        return orderRepository.search(status, customerId, start, end, pageable).map(OrderResponse::from);
    }

    @Transactional
    public OrderResponse pay(UUID id, PaymentMethod method, Instant changedAt) {
        Order order = findLockedOrder(id);
        moveTo(order, OrderStatus.PAID, changedAt);
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrderId(order.getId());
        payment.setMethod(method);
        payment.setAmount(order.getTotal());
        payment.setPaidAt(changedAt);
        payment.touch(changedAt);
        paymentRepository.save(payment);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse ship(UUID id, ShippingMethod method, String trackingCode, Instant changedAt) {
        Order order = findLockedOrder(id);
        if (!order.getStatus().canMoveTo(OrderStatus.SHIPPED)) {
            throw new BusinessException(UNPROCESSABLE, "Transição de status inválida");
        }
        boolean missingTrackingCode = trackingCode == null || trackingCode.isBlank();
        if (method == ShippingMethod.POST_OFFICE && missingTrackingCode) {
            throw new BusinessException(UNPROCESSABLE, "Código de rastreio obrigatório para envio pelos Correios");
        }
        moveTo(order, OrderStatus.SHIPPED, changedAt);
        Shipment shipment = new Shipment();
        shipment.setId(UUID.randomUUID());
        shipment.setOrderId(order.getId());
        shipment.setMethod(method);
        shipment.setTrackingCode(missingTrackingCode ? null : trackingCode.trim());
        shipment.setShippedAt(changedAt);
        shipment.touch(changedAt);
        shipmentRepository.save(shipment);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse deliver(UUID id, Instant changedAt) {
        Order order = findLockedOrder(id);
        moveTo(order, OrderStatus.DELIVERED, changedAt);
        Shipment shipment = shipmentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new BusinessException(UNPROCESSABLE, "Envio não encontrado"));
        shipment.setDeliveredAt(changedAt);
        shipment.touch(changedAt);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(UUID id, Instant changedAt) {
        Order order = findLockedOrder(id);
        moveTo(order, OrderStatus.CANCELLED, changedAt);
        stockService.reverse(order.getId());
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse updateShippingCost(UUID id, BigDecimal shippingCost, Instant changedAt) {
        Order order = findLockedOrder(id);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(UNPROCESSABLE, "Frete só pode mudar em pedido pendente");
        }
        if (shippingCost == null || shippingCost.signum() < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Frete não pode ser negativo");
        }
        order.setShippingCost(money(shippingCost));
        order.setTotal(money(order.getSubtotal().subtract(order.getDiscount()).add(shippingCost)));
        order.touch(changedAt);
        return OrderResponse.from(order);
    }

    // a trava na linha do pedido impede dois cancelamentos ao mesmo tempo de estornarem o estoque duas vezes
    private Order findLockedOrder(UUID id) {
        Order order = orderRepository.findLockedById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Pedido não encontrado"));
        if (order.isDeleted()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Pedido não encontrado");
        }
        return order;
    }

    private void moveTo(Order order, OrderStatus next, Instant changedAt) {
        if (!order.getStatus().canMoveTo(next)) {
            throw new BusinessException(UNPROCESSABLE, "Transição de status inválida");
        }
        order.setStatus(next);
        order.touch(changedAt);
    }

    private Order findActiveOrder(UUID id) {
        return orderRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Pedido não encontrado"));
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
