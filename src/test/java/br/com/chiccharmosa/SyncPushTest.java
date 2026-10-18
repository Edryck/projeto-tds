package br.com.chiccharmosa;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.chiccharmosa.config.SeedIds;
import br.com.chiccharmosa.dto.CustomerDto.CustomerRequest;
import br.com.chiccharmosa.dto.FinanceDto.CashClosingRequest;
import br.com.chiccharmosa.dto.FinanceDto.CashClosingResponse;
import br.com.chiccharmosa.dto.FinanceDto.EntryResponse;
import br.com.chiccharmosa.dto.OrderDto.OrderRequest;
import br.com.chiccharmosa.dto.OrderDto.OrderResponse;
import br.com.chiccharmosa.dto.OrderDto.PayRequest;
import br.com.chiccharmosa.dto.StockDto.StockEntryRequest;
import br.com.chiccharmosa.dto.SyncDto.Operation;
import br.com.chiccharmosa.dto.SyncDto.PushRequest;
import br.com.chiccharmosa.dto.SyncDto.PushResponse;
import br.com.chiccharmosa.dto.SyncDto.Result;
import br.com.chiccharmosa.entity.AuditLog;
import br.com.chiccharmosa.entity.MovementType;
import br.com.chiccharmosa.entity.OrderStatus;
import br.com.chiccharmosa.entity.PaymentMethod;
import br.com.chiccharmosa.entity.SyncAction;
import br.com.chiccharmosa.entity.SyncEntity;
import br.com.chiccharmosa.entity.SyncStatus;
import br.com.chiccharmosa.repository.AuditLogRepository;
import br.com.chiccharmosa.repository.CustomerRepository;
import br.com.chiccharmosa.repository.OrderRepository;
import br.com.chiccharmosa.repository.StockMovementRepository;
import br.com.chiccharmosa.security.CurrentUser;
import br.com.chiccharmosa.service.CustomerService;
import br.com.chiccharmosa.service.FinancialService;
import br.com.chiccharmosa.service.OrderService;
import br.com.chiccharmosa.service.StockService;
import br.com.chiccharmosa.service.SyncService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SyncPushTest extends IntegrationTest {

    private static final ZoneId STORE_ZONE = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private TestData testData;

    @Autowired
    private SyncService syncService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StockService stockService;

    @Autowired
    private StockMovementRepository movementRepository;

    @Autowired
    private FinancialService financialService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private CurrentUser admin;

    @BeforeEach
    void setUp() {
        admin = testData.loginAs(testData.admin());
    }

    @Test
    void sameBatchTwiceDoesNotDuplicate() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 5);
        CustomerRequest customer = new CustomerRequest(UUID.randomUUID(), "Ana", null, null, null);
        OrderRequest order = testData.orderRequest(customer.id(), variantId, 2);
        PushRequest batch = new PushRequest(List.of(
                operation(SyncEntity.CUSTOMER, SyncAction.CREATE, customer.id(), customer, Instant.now()),
                operation(SyncEntity.ORDER, SyncAction.CREATE, order.id(), order, Instant.now())));

        PushResponse first = syncService.push(batch, admin);
        PushResponse second = syncService.push(batch, admin);

        assertThat(statuses(first)).containsExactly(SyncStatus.APPLIED, SyncStatus.APPLIED);
        assertThat(second.results()).isEqualTo(first.results());
        assertThat(customerRepository.existsById(customer.id())).isTrue();
        assertThat(movementRepository.countByReferenceIdAndType(order.id(), MovementType.SALE)).isEqualTo(1);
        assertThat(stockService.balance(variantId)).isEqualTo(3);
    }

    @Test
    void sameOrderWithNewOperationIdIsNotDuplicated() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 5);
        OrderRequest order = testData.orderRequest(testData.customer(), variantId, 2);

        push(admin, operation(SyncEntity.ORDER, SyncAction.CREATE, order.id(), order, Instant.now()));
        PushResponse again = push(admin, operation(SyncEntity.ORDER, SyncAction.CREATE, order.id(), order, Instant.now()));

        assertThat(statuses(again)).containsExactly(SyncStatus.APPLIED);
        assertThat(stockService.balance(variantId)).isEqualTo(3);
    }

    @Test
    void olderChangeIsIgnored() {
        UUID id = customerService.create(new CustomerRequest(UUID.randomUUID(), "Original", null, null, null), Instant.now()).id();
        CustomerRequest older = new CustomerRequest(id, "Antigo", null, null, null);

        PushResponse response = push(admin, operation(SyncEntity.CUSTOMER, SyncAction.UPDATE, id, older, Instant.now().minus(Duration.ofHours(1))));

        assertThat(statuses(response)).containsExactly(SyncStatus.IGNORED);
        assertThat(customerService.get(id).name()).isEqualTo("Original");
    }

    @Test
    void newerChangeWins() {
        Instant twoHoursAgo = Instant.now().minus(Duration.ofHours(2));
        UUID id = customerService.create(new CustomerRequest(UUID.randomUUID(), "Original", null, null, null), twoHoursAgo).id();
        CustomerRequest newer = new CustomerRequest(id, "Novo", null, null, null);

        PushResponse response = push(admin, operation(SyncEntity.CUSTOMER, SyncAction.UPDATE, id, newer, Instant.now().minus(Duration.ofHours(1))));

        assertThat(statuses(response)).containsExactly(SyncStatus.APPLIED);
        assertThat(customerService.get(id).name()).isEqualTo("Novo");
    }

    @Test
    void futureClientTimeIsCappedToServerTime() {
        Instant hourAgo = Instant.now().minus(Duration.ofHours(1));
        UUID id = customerService.create(new CustomerRequest(UUID.randomUUID(), "Original", null, null, null), hourAgo).id();
        CustomerRequest future = new CustomerRequest(id, "Futuro", null, null, null);

        PushResponse response = push(admin, operation(SyncEntity.CUSTOMER, SyncAction.UPDATE, id, future, Instant.now().plus(Duration.ofHours(1))));
        Instant afterPush = Instant.now();

        assertThat(statuses(response)).containsExactly(SyncStatus.APPLIED);
        assertThat(customerRepository.findById(id).orElseThrow().getChangedAt()).isBeforeOrEqualTo(afterPush);
    }

    @Test
    void updateOfDeletedRecordIsIgnored() {
        UUID id = customerService.create(new CustomerRequest(UUID.randomUUID(), "Excluída", null, null, null), Instant.now()).id();
        customerService.delete(id, Instant.now());
        CustomerRequest update = new CustomerRequest(id, "Ressuscitada", null, null, null);

        PushResponse response = push(admin, operation(SyncEntity.CUSTOMER, SyncAction.UPDATE, id, update, Instant.now()));

        assertThat(statuses(response)).containsExactly(SyncStatus.IGNORED);
    }

    @Test
    void operationWithoutPermissionIsRejected() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 5);
        CurrentUser seller = testData.loginAs(testData.userWith("sync:use", "customer:edit"));
        CustomerRequest customer = new CustomerRequest(UUID.randomUUID(), "Bia", null, null, null);
        OrderRequest order = testData.orderRequest(customer.id(), variantId, 1);

        PushResponse response = push(seller,
                operation(SyncEntity.CUSTOMER, SyncAction.CREATE, customer.id(), customer, Instant.now()),
                operation(SyncEntity.ORDER, SyncAction.CREATE, order.id(), order, Instant.now()));

        assertThat(statuses(response)).containsExactly(SyncStatus.APPLIED, SyncStatus.REJECTED);
        assertThat(response.results().get(1).reason()).isNotBlank();
        assertThat(orderRepository.existsById(order.id())).isFalse();
        assertThat(stockService.balance(variantId)).isEqualTo(5);
    }

    @Test
    void invalidStatusTransitionIsRejected() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 5);
        OrderResponse order = orderService.create(testData.orderRequest(testData.customer(), variantId, 1), Instant.now(), false);

        PushResponse response = push(admin, operation(SyncEntity.ORDER, SyncAction.DELIVER, order.id(), null, Instant.now()));

        assertThat(statuses(response)).containsExactly(SyncStatus.REJECTED);
        assertThat(orderService.get(order.id()).status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void rejectedOperationDoesNotUndoAppliedOnes() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 5);
        OrderResponse pending = orderService.create(testData.orderRequest(testData.customer(), variantId, 1), Instant.now(), false);
        CustomerRequest before = new CustomerRequest(UUID.randomUUID(), "Antes", null, null, null);
        CustomerRequest after = new CustomerRequest(UUID.randomUUID(), "Depois", null, null, null);

        PushResponse response = push(admin,
                operation(SyncEntity.CUSTOMER, SyncAction.CREATE, before.id(), before, Instant.now()),
                operation(SyncEntity.ORDER, SyncAction.DELIVER, pending.id(), null, Instant.now()),
                operation(SyncEntity.CUSTOMER, SyncAction.CREATE, after.id(), after, Instant.now()));

        assertThat(statuses(response)).containsExactly(SyncStatus.APPLIED, SyncStatus.REJECTED, SyncStatus.APPLIED);
        assertThat(customerRepository.existsById(before.id())).isTrue();
        assertThat(customerRepository.existsById(after.id())).isTrue();
    }

    @Test
    void offlineSaleOfLastUnitIsKeptAndAlertsOnlyForSecond() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 1);
        UUID customerId = testData.customer();
        OrderRequest firstOrder = testData.orderRequest(customerId, variantId, 1);
        OrderRequest secondOrder = testData.orderRequest(customerId, variantId, 1);

        PushResponse response = push(admin,
                operation(SyncEntity.ORDER, SyncAction.CREATE, firstOrder.id(), firstOrder, Instant.now()),
                operation(SyncEntity.ORDER, SyncAction.CREATE, secondOrder.id(), secondOrder, Instant.now()));

        assertThat(statuses(response)).containsExactly(SyncStatus.APPLIED, SyncStatus.APPLIED);
        assertThat(stockService.balance(variantId)).isEqualTo(-1);
        assertThat(orderRepository.existsById(firstOrder.id())).isTrue();
        assertThat(orderRepository.existsById(secondOrder.id())).isTrue();
        assertThat(auditLogRepository.findByEntityIdAndAction(firstOrder.id(), "STOCK_DIVERGENCE")).isEmpty();
        assertThat(auditLogRepository.findByEntityIdAndAction(secondOrder.id(), "STOCK_DIVERGENCE")).hasSize(1);
    }

    @Test
    void stockEntryReplayDoesNotAddStockTwice() {
        UUID variantId = testData.variant("50.00", "20.00").id();
        StockEntryRequest entry = testData.entryRequest(variantId, 5);
        Operation operation = operation(SyncEntity.STOCK_ENTRY, SyncAction.CREATE, entry.id(), entry, Instant.now());

        push(admin, operation);
        push(admin, operation);
        push(admin, operation(SyncEntity.STOCK_ENTRY, SyncAction.CREATE, entry.id(), entry, Instant.now()));

        assertThat(stockService.balance(variantId)).isEqualTo(5);
    }

    @Test
    void offlinePayOnClosedDayMovesIncomeToTodayAndAudits() {
        LocalDate closedDay = testData.uniqueDay();
        CashClosingResponse closing = financialService.close(new CashClosingRequest(UUID.randomUUID(), SeedIds.STORE_ACCOUNT, closedDay));
        UUID variantId = testData.variantWithStock("50.00", "20.00", 5);
        OrderResponse order = orderService.create(testData.orderRequest(testData.customer(), variantId, 1), Instant.now(), false);
        Instant clientTime = closedDay.atTime(12, 0).atZone(STORE_ZONE).toInstant();

        PushResponse response = push(admin, operation(SyncEntity.ORDER, SyncAction.PAY, order.id(), new PayRequest(PaymentMethod.PIX), clientTime));

        assertThat(statuses(response)).containsExactly(SyncStatus.APPLIED);
        List<EntryResponse> entries = financialService.findByOrder(order.id());
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).entryDate()).isEqualTo(LocalDate.now(STORE_ZONE));
        assertThat(financialService.getClosing(closing.id()).pixTotal()).isEqualByComparingTo(closing.pixTotal());
        List<AuditLog> moved = auditLogRepository.findByEntityIdAndAction(entries.get(0).id(), "INCOME_DATE_MOVED");
        assertThat(moved).hasSize(1);
        assertThat(moved.get(0).getActedAt()).isEqualTo(clientTime);
    }

    @Test
    void orderCreateCarriesShippingCost() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 5);
        OrderRequest order = testData.orderRequest(testData.customer(), variantId, 1, "12.50", null);

        PushResponse response = push(admin, operation(SyncEntity.ORDER, SyncAction.CREATE, order.id(), order, Instant.now()));

        assertThat(statuses(response)).containsExactly(SyncStatus.APPLIED);
        OrderResponse saved = orderService.get(order.id());
        assertThat(saved.shippingCost()).isEqualByComparingTo("12.50");
        assertThat(saved.total()).isEqualByComparingTo("62.50");
    }

    private Operation operation(SyncEntity entity, SyncAction action, UUID entityId, Object payload, Instant clientTime) {
        JsonNode node = payload == null ? null : objectMapper.valueToTree(payload);
        return new Operation(UUID.randomUUID(), entity, action, entityId, node, clientTime);
    }

    private PushResponse push(CurrentUser user, Operation... operations) {
        return syncService.push(new PushRequest(List.of(operations)), user);
    }

    private List<SyncStatus> statuses(PushResponse response) {
        List<SyncStatus> statuses = new ArrayList<>();
        for (Result result : response.results()) {
            statuses.add(result.status());
        }
        return statuses;
    }
}
