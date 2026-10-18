package br.com.chiccharmosa.service;

import br.com.chiccharmosa.dto.CustomerDto.CustomerRequest;
import br.com.chiccharmosa.dto.CustomerDto.CustomerResponse;
import br.com.chiccharmosa.dto.FinanceDto.EntryRequest;
import br.com.chiccharmosa.dto.FinanceDto.EntryResponse;
import br.com.chiccharmosa.dto.OrderDto.ItemResponse;
import br.com.chiccharmosa.dto.OrderDto.OrderRequest;
import br.com.chiccharmosa.dto.OrderDto.OrderResponse;
import br.com.chiccharmosa.dto.OrderDto.PayRequest;
import br.com.chiccharmosa.dto.OrderDto.ShipRequest;
import br.com.chiccharmosa.dto.ProductDto.ProductRequest;
import br.com.chiccharmosa.dto.ProductDto.ProductResponse;
import br.com.chiccharmosa.dto.StockDto.StockEntryRequest;
import br.com.chiccharmosa.dto.StockDto.StockEntryResponse;
import br.com.chiccharmosa.dto.SyncDto.Item;
import br.com.chiccharmosa.dto.SyncDto.Operation;
import br.com.chiccharmosa.dto.SyncDto.PullResponse;
import br.com.chiccharmosa.dto.SyncDto.PushRequest;
import br.com.chiccharmosa.dto.SyncDto.PushResponse;
import br.com.chiccharmosa.dto.SyncDto.Result;
import br.com.chiccharmosa.dto.SyncDto.VariantCreatePayload;
import br.com.chiccharmosa.dto.VariantDto.VariantRequest;
import br.com.chiccharmosa.dto.VariantDto.VariantResponse;
import br.com.chiccharmosa.entity.BaseEntity;
import br.com.chiccharmosa.entity.Customer;
import br.com.chiccharmosa.entity.FinancialEntry;
import br.com.chiccharmosa.entity.Order;
import br.com.chiccharmosa.entity.Product;
import br.com.chiccharmosa.entity.ProductVariant;
import br.com.chiccharmosa.entity.StockEntry;
import br.com.chiccharmosa.entity.SyncAction;
import br.com.chiccharmosa.entity.SyncEntity;
import br.com.chiccharmosa.entity.SyncOperation;
import br.com.chiccharmosa.entity.SyncStatus;
import br.com.chiccharmosa.exception.BusinessException;
import br.com.chiccharmosa.repository.CustomerRepository;
import br.com.chiccharmosa.repository.FinancialEntryRepository;
import br.com.chiccharmosa.repository.OrderRepository;
import br.com.chiccharmosa.repository.ProductRepository;
import br.com.chiccharmosa.repository.ProductVariantRepository;
import br.com.chiccharmosa.repository.StockEntryRepository;
import br.com.chiccharmosa.repository.SyncOperationRepository;
import br.com.chiccharmosa.security.CurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class SyncService {

    private static final HttpStatus UNPROCESSABLE = HttpStatus.valueOf(422);
    private static final Duration PULL_OVERLAP = Duration.ofSeconds(5);
    private static final int MAX_PULL_SIZE = 500;

    private final SyncOperationRepository syncOperationRepository;
    private final CustomerService customerService;
    private final CatalogService catalogService;
    private final FinancialService financialService;
    private final StockService stockService;
    private final OrderService orderService;
    private final AuditService auditService;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final TransactionTemplate newTransaction;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final FinancialEntryRepository financialEntryRepository;
    private final StockEntryRepository stockEntryRepository;
    private final OrderRepository orderRepository;

    public SyncService(SyncOperationRepository syncOperationRepository, CustomerService customerService,
            CatalogService catalogService, FinancialService financialService, StockService stockService,
            OrderService orderService, AuditService auditService, EntityManager entityManager,
            ObjectMapper objectMapper, Validator validator, PlatformTransactionManager transactionManager,
            CustomerRepository customerRepository, ProductRepository productRepository,
            ProductVariantRepository variantRepository, FinancialEntryRepository financialEntryRepository,
            StockEntryRepository stockEntryRepository, OrderRepository orderRepository) {
        this.syncOperationRepository = syncOperationRepository;
        this.customerService = customerService;
        this.catalogService = catalogService;
        this.financialService = financialService;
        this.stockService = stockService;
        this.orderService = orderService;
        this.auditService = auditService;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.financialEntryRepository = financialEntryRepository;
        this.stockEntryRepository = stockEntryRepository;
        this.orderRepository = orderRepository;
    }

    public PushResponse push(PushRequest request, CurrentUser user) {
        List<Result> results = new ArrayList<>();
        for (Operation operation : request.operations()) {
            results.add(process(operation, user));
        }
        return new PushResponse(results);
    }

    @Transactional(readOnly = true)
    public PullResponse pull(Instant since, UUID afterId, int size, CurrentUser user) {
        Instant serverTime = Instant.now();
        int pageSize = Math.max(1, Math.min(size, MAX_PULL_SIZE));
        Limit limit = Limit.of(pageSize);
        // transação que commita tarde pode gravar updated_at antes do since, então o começo da sessão relê 5 s
        Instant from = afterId == null ? since.minus(PULL_OVERLAP) : since;
        Set<String> permissions = user.permissions();
        List<Item> items = new ArrayList<>();

        if (permissions.contains("customer:view")) {
            List<Customer> customers = afterId == null
                    ? customerRepository.findChangedSince(from, limit)
                    : customerRepository.findChangedAfter(from, afterId, limit);
            for (Customer customer : customers) {
                items.add(item(SyncEntity.CUSTOMER, customer, CustomerResponse.from(customer)));
            }
        }
        if (permissions.contains("product:view")) {
            List<Product> products = afterId == null
                    ? productRepository.findChangedSince(from, limit)
                    : productRepository.findChangedAfter(from, afterId, limit);
            for (Product product : products) {
                items.add(item(SyncEntity.PRODUCT, product, ProductResponse.from(product)));
            }
            List<ProductVariant> variants = afterId == null
                    ? variantRepository.findChangedSince(from, limit)
                    : variantRepository.findChangedAfter(from, afterId, limit);
            List<UUID> variantIds = new ArrayList<>();
            for (ProductVariant variant : variants) {
                variantIds.add(variant.getId());
            }
            Map<UUID, Integer> balances = stockService.balances(variantIds);
            for (ProductVariant variant : variants) {
                items.add(item(SyncEntity.VARIANT, variant, VariantResponse.from(variant, balances.get(variant.getId()))));
            }
        }
        if (permissions.contains("finance:view")) {
            List<FinancialEntry> entries = afterId == null
                    ? financialEntryRepository.findChangedSince(from, limit)
                    : financialEntryRepository.findChangedAfter(from, afterId, limit);
            for (FinancialEntry entry : entries) {
                items.add(item(SyncEntity.FINANCIAL_ENTRY, entry, EntryResponse.from(entry)));
            }
        }
        if (permissions.contains("stock:view")) {
            List<StockEntry> entries = afterId == null
                    ? stockEntryRepository.findChangedSince(from, limit)
                    : stockEntryRepository.findChangedAfter(from, afterId, limit);
            for (StockEntry entry : entries) {
                items.add(item(SyncEntity.STOCK_ENTRY, entry, StockEntryResponse.from(entry)));
            }
        }
        if (permissions.contains("order:view")) {
            List<Order> orders = afterId == null
                    ? orderRepository.findChangedSince(from, limit)
                    : orderRepository.findChangedAfter(from, afterId, limit);
            for (Order order : orders) {
                items.add(item(SyncEntity.ORDER, order, OrderResponse.from(order)));
            }
        }

        // UUID.compareTo do Java usa sinal; o texto em hex segue a mesma ordem do uuid no Postgres
        items.sort(Comparator.comparing(Item::updatedAt).thenComparing(item -> item.id().toString()));
        if (items.size() > pageSize) {
            items = new ArrayList<>(items.subList(0, pageSize));
        }
        if (items.isEmpty()) {
            return new PullResponse(serverTime, since, afterId, false, items);
        }
        Item last = items.get(items.size() - 1);
        return new PullResponse(serverTime, last.updatedAt(), last.id(), items.size() == pageSize, items);
    }

    private Item item(SyncEntity entity, BaseEntity record, Object data) {
        if (record.isDeleted()) {
            return new Item(entity, record.getId(), true, null, record.getUpdatedAt());
        }
        return new Item(entity, record.getId(), false, data, record.getUpdatedAt());
    }

    private Result process(Operation operation, CurrentUser user) {
        SyncOperation done = syncOperationRepository.findById(operation.id()).orElse(null);
        if (done != null) {
            return new Result(done.getId(), done.getStatus(), done.getReason());
        }
        String permission = requiredPermission(operation.entity(), operation.action());
        if (permission == null) {
            return reject(operation, user, "Operação não suportada");
        }
        if (!user.permissions().contains(permission)) {
            return reject(operation, user, "Sem permissão");
        }
        Instant now = Instant.now();
        // relógio do celular mente, então limito pelo do servidor
        Instant effective = operation.clientTime().isAfter(now) ? now : operation.clientTime();
        try {
            return newTransaction.execute(status -> applyAndRecord(operation, user, effective));
        } catch (BusinessException exception) {
            return reject(operation, user, exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            return reject(operation, user, "Conflito com dados existentes");
        }
    }

    private Result applyAndRecord(Operation operation, CurrentUser user, Instant effective) {
        boolean isEdit = operation.action() == SyncAction.UPDATE || operation.action() == SyncAction.DELETE;
        if (isEdit && !isNewest(operation, effective)) {
            return record(operation, user, SyncStatus.IGNORED, null);
        }
        apply(operation, effective);
        return record(operation, user, SyncStatus.APPLIED, null);
    }

    private Result reject(Operation operation, CurrentUser user, String reason) {
        return newTransaction.execute(status -> record(operation, user, SyncStatus.REJECTED, reason));
    }

    private Result record(Operation operation, CurrentUser user, SyncStatus status, String reason) {
        SyncOperation saved = new SyncOperation();
        saved.setId(operation.id());
        saved.setUserId(user.userId());
        saved.setDeviceId(user.deviceId());
        saved.setEntity(operation.entity());
        saved.setAction(operation.action());
        saved.setStatus(status);
        saved.setReason(reason);
        syncOperationRepository.save(saved);
        return new Result(operation.id(), status, reason);
    }

    private String requiredPermission(SyncEntity entity, SyncAction action) {
        boolean isCrud = action == SyncAction.CREATE || action == SyncAction.UPDATE || action == SyncAction.DELETE;
        return switch (entity) {
            case CUSTOMER -> isCrud ? "customer:edit" : null;
            case PRODUCT, VARIANT -> isCrud ? "product:edit" : null;
            case FINANCIAL_ENTRY -> isCrud ? "finance:edit" : null;
            case STOCK_ENTRY -> action == SyncAction.CREATE ? "stock:adjust" : null;
            case ORDER -> switch (action) {
                case CREATE -> "order:create";
                case PAY -> "order:pay";
                case SHIP, DELIVER -> "order:ship";
                case CANCEL -> "order:cancel";
                default -> null;
            };
        };
    }

    private boolean isNewest(Operation operation, Instant effective) {
        BaseEntity current = entityManager.find(entityClass(operation.entity()), operation.entityId());
        if (current == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Registro não encontrado");
        }
        if (current.isDeleted()) {
            return false;
        }
        return effective.isAfter(current.getChangedAt());
    }

    private Class<? extends BaseEntity> entityClass(SyncEntity entity) {
        return switch (entity) {
            case CUSTOMER -> Customer.class;
            case PRODUCT -> Product.class;
            case VARIANT -> ProductVariant.class;
            case FINANCIAL_ENTRY -> FinancialEntry.class;
            case STOCK_ENTRY -> StockEntry.class;
            case ORDER -> Order.class;
        };
    }

    private void apply(Operation operation, Instant effective) {
        switch (operation.entity()) {
            case CUSTOMER -> applyCustomer(operation, effective);
            case PRODUCT -> applyProduct(operation, effective);
            case VARIANT -> applyVariant(operation, effective);
            case FINANCIAL_ENTRY -> applyFinancialEntry(operation, effective);
            case STOCK_ENTRY -> applyStockEntry(operation, effective);
            case ORDER -> applyOrder(operation, effective);
        }
    }

    private void applyCustomer(Operation operation, Instant effective) {
        UUID id = operation.entityId();
        switch (operation.action()) {
            case CREATE -> {
                CustomerRequest request = read(operation, CustomerRequest.class);
                checkSameId(request.id(), id);
                customerService.create(request, effective);
            }
            case UPDATE -> customerService.update(id, read(operation, CustomerRequest.class), effective);
            case DELETE -> customerService.delete(id, effective);
            default -> throw unsupported();
        }
    }

    private void applyProduct(Operation operation, Instant effective) {
        UUID id = operation.entityId();
        switch (operation.action()) {
            case CREATE -> {
                ProductRequest request = read(operation, ProductRequest.class);
                checkSameId(request.id(), id);
                catalogService.createProduct(request, effective);
            }
            case UPDATE -> catalogService.updateProduct(id, read(operation, ProductRequest.class), effective);
            case DELETE -> catalogService.deleteProduct(id, effective);
            default -> throw unsupported();
        }
    }

    private void applyVariant(Operation operation, Instant effective) {
        UUID id = operation.entityId();
        switch (operation.action()) {
            case CREATE -> {
                VariantCreatePayload payload = read(operation, VariantCreatePayload.class);
                checkSameId(payload.variant().id(), id);
                catalogService.createVariant(payload.productId(), payload.variant(), effective);
            }
            case UPDATE -> catalogService.updateVariant(id, read(operation, VariantRequest.class), effective);
            case DELETE -> catalogService.deleteVariant(id, effective);
            default -> throw unsupported();
        }
    }

    private void applyFinancialEntry(Operation operation, Instant effective) {
        UUID id = operation.entityId();
        switch (operation.action()) {
            case CREATE -> {
                EntryRequest request = read(operation, EntryRequest.class);
                checkSameId(request.id(), id);
                financialService.create(request, effective);
            }
            case UPDATE -> financialService.update(id, read(operation, EntryRequest.class), effective);
            case DELETE -> financialService.delete(id, effective);
            default -> throw unsupported();
        }
    }

    private void applyStockEntry(Operation operation, Instant effective) {
        if (operation.action() != SyncAction.CREATE) {
            throw unsupported();
        }
        StockEntryRequest request = read(operation, StockEntryRequest.class);
        checkSameId(request.id(), operation.entityId());
        stockService.registerEntry(request, effective);
    }

    private void applyOrder(Operation operation, Instant effective) {
        UUID id = operation.entityId();
        switch (operation.action()) {
            case CREATE -> createOrder(operation, effective);
            case PAY -> orderService.pay(id, read(operation, PayRequest.class).method(), effective);
            case SHIP -> {
                ShipRequest request = read(operation, ShipRequest.class);
                orderService.ship(id, request.method(), request.trackingCode(), effective);
            }
            case DELIVER -> orderService.deliver(id, effective);
            case CANCEL -> orderService.cancel(id, effective);
            default -> throw unsupported();
        }
    }

    private void createOrder(Operation operation, Instant effective) {
        OrderRequest request = read(operation, OrderRequest.class);
        checkSameId(request.id(), operation.entityId());
        if (orderService.exists(request.id())) {
            return;
        }
        OrderResponse order = orderService.create(request, effective, true);
        Set<UUID> variantIds = new LinkedHashSet<>();
        for (ItemResponse item : order.items()) {
            variantIds.add(item.variantId());
        }
        for (UUID variantId : variantIds) {
            int balance = stockService.balance(variantId);
            if (balance < 0) {
                auditService.record(AuditService.ORDER, order.id(), AuditService.STOCK_DIVERGENCE, null,
                        Map.of("variantId", variantId, "balance", balance), effective);
            }
        }
    }

    private <T> T read(Operation operation, Class<T> type) {
        JsonNode payload = operation.payload();
        if (payload == null || payload.isNull()) {
            throw invalidData();
        }
        T value;
        try {
            value = objectMapper.treeToValue(payload, type);
        } catch (JacksonException exception) {
            throw invalidData();
        }
        if (!validator.validate(value).isEmpty()) {
            throw invalidData();
        }
        return value;
    }

    private void checkSameId(UUID payloadId, UUID entityId) {
        if (!entityId.equals(payloadId)) {
            throw new BusinessException(UNPROCESSABLE, "Id do registro diferente do informado na operação");
        }
    }

    private BusinessException invalidData() {
        return new BusinessException(UNPROCESSABLE, "Dados inválidos");
    }

    private BusinessException unsupported() {
        return new BusinessException(UNPROCESSABLE, "Operação não suportada");
    }
}
