package br.com.chiccharmosa.service;

import br.com.chiccharmosa.dto.CancellationDto.CancellationView;
import br.com.chiccharmosa.dto.OrderDto.ItemRequest;
import br.com.chiccharmosa.dto.OrderDto.ItemView;
import br.com.chiccharmosa.dto.OrderDto.OrderRequest;
import br.com.chiccharmosa.dto.OrderDto.OrderResponse;
import br.com.chiccharmosa.dto.OrderDto.OrderView;
import br.com.chiccharmosa.dto.OrderDto.PaymentView;
import br.com.chiccharmosa.dto.OrderDto.PendingCancellation;
import br.com.chiccharmosa.dto.OrderDto.ShipmentView;
import br.com.chiccharmosa.entity.CancellationRequest;
import br.com.chiccharmosa.entity.CancellationStatus;
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
import br.com.chiccharmosa.repository.CancellationRequestRepository;
import br.com.chiccharmosa.repository.CouponRepository;
import br.com.chiccharmosa.repository.CustomerRepository;
import br.com.chiccharmosa.repository.OrderRepository;
import br.com.chiccharmosa.repository.PaymentRepository;
import br.com.chiccharmosa.repository.ProductVariantRepository;
import br.com.chiccharmosa.repository.ShipmentRepository;
import br.com.chiccharmosa.repository.UserRepository;
import br.com.chiccharmosa.security.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private final CouponRepository couponRepository;
    private final StockService stockService;
    private final CouponService couponService;
    private final FinancialService financialService;
    private final AuditService auditService;
    private final CancellationRequestRepository cancellationRepository;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository, CustomerRepository customerRepository,
            ProductVariantRepository variantRepository, PaymentRepository paymentRepository,
            ShipmentRepository shipmentRepository, CouponRepository couponRepository, StockService stockService, CouponService couponService,
            FinancialService financialService, AuditService auditService,
            CancellationRequestRepository cancellationRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.variantRepository = variantRepository;
        this.paymentRepository = paymentRepository;
        this.shipmentRepository = shipmentRepository;
        this.couponRepository = couponRepository;
        this.stockService = stockService;
        this.couponService = couponService;
        this.financialService = financialService;
        this.auditService = auditService;
        this.cancellationRepository = cancellationRepository;
        this.userRepository = userRepository;
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
        OrderResponse response = OrderResponse.from(orderRepository.save(order));
        auditService.record(AuditService.ORDER, order.getId(), AuditService.CREATE, null, response, changedAt);
        return response;
    }

    public OrderResponse get(UUID id) {
        return OrderResponse.from(findActiveOrder(id));
    }

    public Page<OrderResponse> list(OrderStatus status, UUID customerId, Instant from, Instant to,
            Pageable pageable) {
        return searchOrders(status, customerId, null, from, to, pageable).map(OrderResponse::from);
    }

    public OrderView getView(UUID id) {
        Order order = findActiveOrder(id);
        Payment payment = paymentRepository.findByOrderId(id).orElse(null);
        Shipment shipment = shipmentRepository.findByOrderId(id).orElse(null);
        CancellationRequest pending = cancellationRepository.findPendingByOrderId(id).orElse(null);
        return toView(order, payment, shipment, pending);
    }

    public Page<OrderView> listViews(OrderStatus status, UUID customerId, String q, Instant from, Instant to,
            Pageable pageable) {
        Page<Order> page = searchOrders(status, customerId, q, from, to, pageable);
        List<UUID> ids = new ArrayList<>();
        for (Order order : page.getContent()) {
            ids.add(order.getId());
        }
        Map<UUID, Payment> payments = new HashMap<>();
        for (Payment payment : paymentRepository.findByOrderIdIn(ids)) {
            payments.put(payment.getOrderId(), payment);
        }
        Map<UUID, Shipment> shipments = new HashMap<>();
        for (Shipment shipment : shipmentRepository.findByOrderIdIn(ids)) {
            shipments.put(shipment.getOrderId(), shipment);
        }
        Map<UUID, CancellationRequest> pendings = new HashMap<>();
        for (CancellationRequest pending : cancellationRepository.findPendingByOrderIds(ids)) {
            pendings.put(pending.getOrder().getId(), pending);
        }
        return page.map(order -> toView(order, payments.get(order.getId()), shipments.get(order.getId()),
                pendings.get(order.getId())));
    }

    private Page<Order> searchOrders(OrderStatus status, UUID customerId, String q, Instant from, Instant to,
            Pageable pageable) {
        // postgres não descobre o tipo de um Instant nulo na query, então troco por limites bem largos
        Instant start = from == null ? Instant.EPOCH : from;
        Instant end = to == null ? FAR_FUTURE : to;
        return orderRepository.search(status, customerId, normalizeSearch(q), start, end, pageable);
    }

    private String normalizeSearch(String q) {
        if (q == null) {
            return "";
        }
        String text = q.trim();
        if (text.startsWith("#")) {
            text = text.substring(1).trim();
        }
        return text.toLowerCase();
    }

    private OrderView toView(Order order, Payment payment, Shipment shipment, CancellationRequest pending) {
        List<ItemView> items = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            items.add(toItemView(item));
        }
        String couponCode = null;
        if (order.getCouponId() != null) {
            couponCode = couponRepository.findById(order.getCouponId()).map(Coupon::getCode).orElse(null);
        }
        PaymentView paymentView = payment == null ? null : new PaymentView(payment.getMethod(), payment.getPaidAt());
        ShipmentView shipmentView = null;
        if (shipment != null) {
            shipmentView = new ShipmentView(shipment.getMethod(), shipment.getTrackingCode(),
                    shipment.getShippedAt(), shipment.getDeliveredAt());
        }
        PendingCancellation pendingCancellation = null;
        if (pending != null) {
            pendingCancellation = new PendingCancellation(pending.getId(), pending.getReason(),
                    pending.getRequestedBy().getName(), pending.getCreatedAt());
        }
        return new OrderView(order.getId(), order.getCustomer().getId(), order.getCustomer().getName(),
                order.getStatus(), items, order.getSubtotal(), order.getDiscount(), order.getShippingCost(),
                order.getTotal(), couponCode, order.getNote(), order.getOrderedAt(), paymentView, shipmentView,
                pendingCancellation);
    }

    private ItemView toItemView(OrderItem item) {
        ProductVariant variant = item.getVariant();
        String label = variant.getSize() + " · " + variant.getColor();
        if (variant.getModel() != null && !variant.getModel().isBlank()) {
            label = label + " · " + variant.getModel();
        }
        return new ItemView(item.getId(), variant.getId(), variant.getProduct().getName(), label, item.getQuantity(),
                item.getUnitPrice(), item.getUnitCost());
    }

    @Transactional
    public OrderResponse pay(UUID id, PaymentMethod method, Instant changedAt) {
        Order order = findLockedOrder(id);
        rejectIfCancellationPending(order);
        OrderResponse before = OrderResponse.from(order);
        moveTo(order, OrderStatus.PAID, changedAt);
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrderId(order.getId());
        payment.setMethod(method);
        payment.setAmount(order.getTotal());
        payment.setPaidAt(changedAt);
        payment.touch(changedAt);
        paymentRepository.save(payment);
        financialService.recordIncome(order, method, changedAt);
        return recordStatusChange(order, before, changedAt);
    }

    @Transactional
    public OrderResponse ship(UUID id, ShippingMethod method, String trackingCode, Instant changedAt) {
        Order order = findLockedOrder(id);
        rejectIfCancellationPending(order);
        if (!order.getStatus().canMoveTo(OrderStatus.SHIPPED)) {
            throw new BusinessException(UNPROCESSABLE, "Transição de status inválida");
        }
        boolean missingTrackingCode = trackingCode == null || trackingCode.isBlank();
        if (method == ShippingMethod.POST_OFFICE && missingTrackingCode) {
            throw new BusinessException(UNPROCESSABLE, "Código de rastreio obrigatório para envio pelos Correios");
        }
        OrderResponse before = OrderResponse.from(order);
        moveTo(order, OrderStatus.SHIPPED, changedAt);
        Shipment shipment = new Shipment();
        shipment.setId(UUID.randomUUID());
        shipment.setOrderId(order.getId());
        shipment.setMethod(method);
        shipment.setTrackingCode(missingTrackingCode ? null : trackingCode.trim());
        shipment.setShippedAt(changedAt);
        shipment.touch(changedAt);
        shipmentRepository.save(shipment);
        return recordStatusChange(order, before, changedAt);
    }

    @Transactional
    public OrderResponse deliver(UUID id, Instant changedAt) {
        Order order = findLockedOrder(id);
        rejectIfCancellationPending(order);
        OrderResponse before = OrderResponse.from(order);
        moveTo(order, OrderStatus.DELIVERED, changedAt);
        Shipment shipment = shipmentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new BusinessException(UNPROCESSABLE, "Envio não encontrado"));
        shipment.setDeliveredAt(changedAt);
        shipment.touch(changedAt);
        return recordStatusChange(order, before, changedAt);
    }

    @Transactional
    public OrderResponse cancel(UUID id, Instant changedAt) {
        Order order = findLockedOrder(id);
        CancellationRequest pending = cancellationRepository.findPendingByOrderId(id).orElse(null);
        return cancelOrder(order, pending, null, changedAt);
    }

    private OrderResponse cancelOrder(Order order, CancellationRequest pending, String response, Instant changedAt) {
        OrderResponse before = OrderResponse.from(order);
        boolean wasPaid = order.getStatus() == OrderStatus.PAID;
        moveTo(order, OrderStatus.CANCELLED, changedAt);
        stockService.reverse(order.getId());
        if (wasPaid) {
            financialService.recordRefund(order, changedAt);
        }
        if (pending != null) {
            decide(pending, CancellationStatus.APPROVED, response, changedAt);
        }
        return recordStatusChange(order, before, changedAt);
    }

    @Transactional
    public CancellationView requestCancellation(UUID orderId, String reason, Instant changedAt) {
        Order order = findLockedOrder(orderId);
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(UNPROCESSABLE, "Pedido não pode ser cancelado");
        }
        if (cancellationRepository.findPendingByOrderId(orderId).isPresent()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Já existe uma solicitação pendente para este pedido");
        }
        CancellationRequest request = new CancellationRequest();
        request.setId(UUID.randomUUID());
        request.setOrder(order);
        request.setReason(reason.trim());
        request.setRequestedBy(userRepository.getReferenceById(CurrentUser.current().userId()));
        request.setStatus(CancellationStatus.PENDING);
        request.touch(changedAt);
        CancellationView view = CancellationView.from(cancellationRepository.save(request));
        auditService.record(AuditService.CANCELLATION_REQUEST, request.getId(), AuditService.CREATE, null, view,
                changedAt);
        return view;
    }

    public Page<CancellationView> listCancellations(CancellationStatus status, Pageable pageable) {
        return cancellationRepository.search(status, pageable).map(CancellationView::from);
    }

    @Transactional
    public CancellationView approveCancellation(UUID id, String response, Instant changedAt) {
        CancellationRequest request = findPendingForDecision(id);
        cancelOrder(request.getOrder(), request, response, changedAt);
        return CancellationView.from(request);
    }

    @Transactional
    public CancellationView rejectCancellation(UUID id, String response, Instant changedAt) {
        CancellationRequest request = findPendingForDecision(id);
        decide(request, CancellationStatus.REJECTED, response, changedAt);
        return CancellationView.from(request);
    }

    // trava o pedido antes de ler a solicitação, assim aprovar, recusar e cancelar direto nunca decidem a mesma ao mesmo tempo
    private CancellationRequest findPendingForDecision(UUID id) {
        UUID orderId = cancellationRepository.findOrderIdById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Solicitação não encontrada"));
        findLockedOrder(orderId);
        CancellationRequest request = cancellationRepository.findById(id).orElseThrow();
        if (request.getStatus() != CancellationStatus.PENDING) {
            throw new BusinessException(HttpStatus.CONFLICT, "Solicitação já decidida");
        }
        return request;
    }

    private void decide(CancellationRequest request, CancellationStatus status, String response, Instant changedAt) {
        CancellationView before = CancellationView.from(request);
        request.setStatus(status);
        request.setDecidedBy(userRepository.getReferenceById(CurrentUser.current().userId()));
        request.setDecidedAt(changedAt);
        request.setResponse(response == null || response.isBlank() ? null : response.trim());
        request.touch(changedAt);
        CancellationView after = CancellationView.from(request);
        auditService.record(AuditService.CANCELLATION_REQUEST, request.getId(), AuditService.STATUS_CHANGE, before,
                after, changedAt);
    }

    private void rejectIfCancellationPending(Order order) {
        if (cancellationRepository.findPendingByOrderId(order.getId()).isPresent()) {
            throw new BusinessException(UNPROCESSABLE, "Pedido com cancelamento pendente");
        }
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
        OrderResponse before = OrderResponse.from(order);
        order.setShippingCost(money(shippingCost));
        order.setTotal(money(order.getSubtotal().subtract(order.getDiscount()).add(shippingCost)));
        order.touch(changedAt);
        OrderResponse after = OrderResponse.from(order);
        auditService.record(AuditService.ORDER, id, AuditService.UPDATE, before, after, changedAt);
        return after;
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

    private OrderResponse recordStatusChange(Order order, OrderResponse before, Instant changedAt) {
        OrderResponse after = OrderResponse.from(order);
        auditService.record(AuditService.ORDER, order.getId(), AuditService.STATUS_CHANGE, before, after, changedAt);
        return after;
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
